package com.sjp.recruitment.config;

import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);
        try {
            String email = jwtUtil.extractUsername(token);
            if (email != null && jwtUtil.validateToken(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepository.findByEmail(email)
                        .filter(user -> isCurrentToken(token, user))
                        .ifPresent(user -> authenticate(request, new UserResponse(
                                String.valueOf(user.getId()),
                                user.getEmail(),
                                user.getRoleEnum() == null ? normalizeRole(user.getRole()) : user.getRoleEnum().name(),
                                user.getStatusEnum() == null ? "UNKNOWN" : user.getStatusEnum().name(),
                                user.isEmailVerified()
                        )));
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, UserResponse user) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + normalizeRole(user.role()))
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean isCurrentToken(String token, User user) {
        String tokenUserId = jwtUtil.extractStringClaim(token, "id");
        Integer tokenVersion = jwtUtil.extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
        return tokenUserId != null
                && tokenUserId.equals(String.valueOf(user.getId()))
                && user.isEmailVerified()
                && user.getStatusEnum() == User.UserStatus.ACTIVE
                && tokenVersion != null
                && tokenVersion.equals(user.getTokenVersion() == null ? 0 : user.getTokenVersion());
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "job_seeker", "candidate" -> "CANDIDATE";
            case "employer" -> "EMPLOYER";
            case "admin" -> "ADMIN";
            default -> role.trim().toUpperCase(Locale.ROOT);
        };
    }
}
