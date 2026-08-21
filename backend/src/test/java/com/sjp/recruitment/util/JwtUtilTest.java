package com.sjp.recruitment.util;

import com.sjp.recruitment.model.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private User user;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "unit-test-secret-key-must-be-at-least-32b!");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("candidate@srp.test");
        user.setRole(User.UserRole.CANDIDATE);
        user.setTokenVersion(2);
    }

    @Test
    void generateToken_containsEmailAndRoleClaims() {
        String token = jwtUtil.generateToken(user);

        assertEquals("candidate@srp.test", jwtUtil.extractUsername(token));
        assertEquals(user.getId().toString(), jwtUtil.extractStringClaim(token, "id"));
        assertEquals("CANDIDATE", jwtUtil.extractStringClaim(token, "role"));
        assertEquals("2", jwtUtil.extractStringClaim(token, "tokenVersion"));
    }

    @Test
    void validateToken_acceptsMatchingActiveUser() {
        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.validateToken(token, user));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_rejectsDifferentEmail() {
        String token = jwtUtil.generateToken(user);
        User other = new User();
        other.setEmail("other@srp.test");

        assertFalse(jwtUtil.validateToken(token, other));
    }

    @Test
    void extractClaim_rejectsTamperedToken() {
        String token = jwtUtil.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThrows(JwtException.class, () -> jwtUtil.extractUsername(tampered));
    }

    @Test
    void extractExpiration_rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1_000L);
        String token = jwtUtil.generateToken(user);

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.extractExpiration(token));
    }

    @Test
    void generateToken_normalizesLegacyCandidateRole() {
        User legacy = new User();
        legacy.setId(UUID.randomUUID());
        legacy.setEmail("legacy@srp.test");
        ReflectionTestUtils.setField(legacy, "role", "job_seeker");

        String token = jwtUtil.generateToken(legacy);

        assertEquals("CANDIDATE", jwtUtil.extractStringClaim(token, "role"));
    }

    @Test
    void extractStringClaim_returnsNullWhenMissing() {
        String token = jwtUtil.generateToken(user);

        assertNotNull(jwtUtil.extractExpiration(token));
        assertEquals(null, jwtUtil.extractStringClaim(token, "missing"));
    }

    @Test
    void generateToken_defaultsTokenVersionToZero() {
        user.setTokenVersion(null);
        String token = jwtUtil.generateToken(user);

        assertEquals("0", jwtUtil.extractStringClaim(token, "tokenVersion"));
    }
}
