package com.sjp.recruitment.util;

import com.sjp.recruitment.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractStringClaim(String token, String claimName) {
        Object value = extractAllClaims(token).get(claimName);
        return value == null ? null : String.valueOf(value);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                .claim("role", user.getRoleEnum() == null ? normalizeRole(user.getRole()) : user.getRoleEnum().name())
                .claim("tokenVersion", user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, User user) {
        return !isTokenExpired(token) && extractUsername(token).equals(user.getEmail());
    }

    public Boolean validateToken(String token) {
        return !isTokenExpired(token) && extractUsername(token) != null;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "job_seeker", "candidate" -> "CANDIDATE";
            case "employer" -> "EMPLOYER";
            case "admin" -> "ADMIN";
            default -> role.trim().toUpperCase(Locale.ROOT);
        };
    }
}
