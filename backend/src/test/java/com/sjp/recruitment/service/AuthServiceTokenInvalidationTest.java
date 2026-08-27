package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.request.ResetPasswordRequest;
import com.sjp.recruitment.model.entity.PasswordResetToken;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.EmailVerificationTokenRepository;
import com.sjp.recruitment.repository.OauthAccountRepository;
import com.sjp.recruitment.repository.PasswordResetTokenRepository;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.service.storage.StorageService;
import com.sjp.recruitment.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTokenInvalidationTest {

    @Mock private UserRepository userRepository;
    @Mock private CandidateProfileRepository candidateProfileRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private OauthAccountRepository oauthAccountRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private DtoMapper dtoMapper;
    @Mock private EmailService emailService;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private StorageService storageService;

    @InjectMocks private AuthService authService;

    @Test
    void resetPasswordBumpsTokenVersionInTheSameTransaction() throws Exception {
        User user = new User();
        user.setTokenVersion(3);
        user.setPasswordHash("old-hash");

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        String rawToken = "raw-reset-token";
        when(passwordResetTokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.resetPassword(new ResetPasswordRequest(rawToken, "NewPassword1"));

        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(4, user.getTokenVersion());
        assertNotNull(token.getUsedAt());
        verify(userRepository).save(user);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
