package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompleteOauthRoleRequest;
import com.sjp.recruitment.model.dto.request.ForgotPasswordRequest;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.request.LoginRequest;
import com.sjp.recruitment.model.dto.request.RegisterRequest;
import com.sjp.recruitment.model.dto.request.ResetPasswordRequest;
import com.sjp.recruitment.model.dto.response.AuthResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.EmailVerificationToken;
import com.sjp.recruitment.model.entity.OauthRoleSelectionToken;
import com.sjp.recruitment.model.entity.PasswordResetToken;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.EmailVerificationTokenRepository;
import com.sjp.recruitment.repository.PasswordResetTokenRepository;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DtoMapper dtoMapper;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.email-verification-token-ttl-minutes}")
    private long verificationTtlMinutes;

    @Value("${app.password-reset-token-ttl-minutes:30}")
    private long passwordResetTtlMinutes;

    private final Map<String, OauthRoleSelectionToken> oauthRoleSelectionTokens = new ConcurrentHashMap<>();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email da ton tai");
        }
        validatePassword(request.getPassword());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setStatus(User.UserStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        if (savedUser.getRoleEnum() == User.UserRole.CANDIDATE) {
            ensureCandidateProfile(savedUser);
        }

        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(savedUser);
        token.setToken(hashToken(tokenValue));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(verificationTtlMinutes));
        emailVerificationTokenRepository.save(token);

        String verificationLink = frontendBaseUrl + "/verify-email?token=" + tokenValue;
        emailService.sendVerificationEmail(savedUser.getEmail(), verificationLink);

        return new AuthResponse(dtoMapper.toUserResponse(savedUser), null);
    }

    @Transactional
    public UserResponse verifyEmail(String tokenValue) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(hashToken(tokenValue))
                .or(() -> emailVerificationTokenRepository.findByToken(tokenValue))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN", "Link xac minh khong hop le"));
        if (token.getUsedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_USED", "Link xac minh da duoc su dung");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_EXPIRED", "Link xac minh da het han");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        token.setUsedAt(LocalDateTime.now());
        return dtoMapper.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        RemoteUser user = findRemoteUserByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoac mat khau khong dung"));

        if (user.passwordHash() == null || !passwordMatches(request.getPassword(), user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoac mat khau khong dung");
        }
        if (!user.emailVerified() || !"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Email chua duoc xac minh");
        }

        namedParameterJdbcTemplate.update(
                "UPDATE users SET last_login_at = now(), updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", UUID.fromString(user.id()))
        );

        UserResponse response = user.toUserResponse();
        return new AuthResponse(response, jwtUtil.generateToken(response));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                return;
            }
            String tokenValue = UUID.randomUUID().toString();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(hashToken(tokenValue));
            token.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTtlMinutes));
            passwordResetTokenRepository.save(token);
            emailService.sendPasswordResetEmail(user.getEmail(), frontendBaseUrl + "/reset-password?token=" + tokenValue);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashToken(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Link dat lai mat khau khong hop le"));
        if (token.getUsedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_USED", "Link dat lai mat khau da duoc su dung");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_EXPIRED", "Link dat lai mat khau da het han");
        }
        validatePassword(request.password());
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        token.setUsedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse completeOauthRole(CompleteOauthRoleRequest request) {
        OauthRoleSelectionToken token = Optional.ofNullable(oauthRoleSelectionTokens.get(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OAUTH_TOKEN", "Phien chon vai tro khong hop le"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OAUTH_TOKEN_EXPIRED", "Phien chon vai tro da het han");
        }
        if (userRepository.existsByEmail(token.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email da ton tai");
        }

        User user = new User();
        user.setEmail(token.getEmail());
        user.setRole(request.role());
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        if (saved.getRoleEnum() == User.UserRole.CANDIDATE) {
            CandidateProfile profile = ensureCandidateProfile(saved);
            profile.setFullName(token.getFullName());
        }
        token.setUsedAt(LocalDateTime.now());
        oauthRoleSelectionTokens.remove(request.token());
        return new AuthResponse(dtoMapper.toUserResponse(saved), jwtUtil.generateToken(saved));
    }

    @Transactional
    public OauthRoleSelectionToken createOauthRoleSelectionToken(String email, String provider, String providerId, String fullName, long ttlMinutes) {
        OauthRoleSelectionToken token = new OauthRoleSelectionToken();
        token.setEmail(email);
        token.setProvider(provider);
        token.setProviderId(providerId);
        token.setFullName(fullName);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        oauthRoleSelectionTokens.put(token.getToken(), token);
        return token;
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        if (principal instanceof UserResponse user) {
            return userRepository.findByEmail(user.email())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Khong tim thay nguoi dung"));
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Khong tim thay nguoi dung"));
    }

    public UserResponse getCurrentUserResponse() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Chua dang nhap");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserResponse user) {
            return user;
        }
        if (principal instanceof User user) {
            return dtoMapper.toUserResponse(user);
        }

        return findRemoteUserByEmail(authentication.getName())
                .map(RemoteUser::toUserResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Khong tim thay nguoi dung"));
    }

    public CandidateProfile ensureCandidateProfile(User user) {
        return candidateProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    CandidateProfile profile = new CandidateProfile();
                    profile.setUser(user);
                    profile.setSkills(List.of());
                    profile.setEducation(List.of());
                    profile.setWorkExperience(List.of());
                    profile.setProjects(List.of());
                    profile.setCertifications(List.of());
                    return candidateProfileRepository.save(profile);
                });
    }

    private Optional<RemoteUser> findRemoteUserByEmail(String email) {
        String sql = """
                SELECT
                    id::text AS id,
                    email,
                    password_hash,
                    role,
                    status,
                    email_verified_at
                FROM users
                WHERE LOWER(email) = LOWER(:email)
                LIMIT 1
                """;
        List<RemoteUser> users = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("email", email),
                this::mapRemoteUser
        );
        return users.stream().findFirst();
    }

    private RemoteUser mapRemoteUser(ResultSet resultSet, int rowNumber) throws SQLException {
        boolean emailVerified = resultSet.getTimestamp("email_verified_at") != null;
        return new RemoteUser(
                resultSet.getString("id"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                toFrontendRole(resultSet.getString("role")),
                toFrontendStatus(resultSet.getString("status")),
                emailVerified
        );
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "Mat khau phai co it nhat 8 ky tu, gom chu hoa, chu thuong va so");
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toFrontendRole(String role) {
        if (role == null) {
            return "CANDIDATE";
        }
        return switch (role.trim().toLowerCase(Locale.ROOT)) {
            case "job_seeker", "candidate" -> "CANDIDATE";
            case "employer" -> "EMPLOYER";
            case "admin" -> "ADMIN";
            default -> role.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String toFrontendStatus(String status) {
        if (status == null) {
            return "PENDING_VERIFICATION";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "active" -> "ACTIVE";
            case "suspended" -> "SUSPENDED";
            default -> "PENDING_VERIFICATION";
        };
    }

    private record RemoteUser(
            String id,
            String email,
            String passwordHash,
            String role,
            String status,
            boolean emailVerified
    ) {
        UserResponse toUserResponse() {
            return new UserResponse(id, email, role, status, emailVerified);
        }
    }
}
