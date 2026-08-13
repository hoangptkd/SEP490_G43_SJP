package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.ChangePasswordRequest;
import com.sjp.recruitment.model.dto.request.CompleteOauthRoleRequest;
import com.sjp.recruitment.model.dto.request.DeactivateAccountRequest;
import com.sjp.recruitment.model.dto.request.ForgotPasswordRequest;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.request.LoginRequest;
import com.sjp.recruitment.model.dto.request.RegisterRequest;
import com.sjp.recruitment.model.dto.request.ResetPasswordRequest;
import com.sjp.recruitment.model.dto.response.AccountResponse;
import com.sjp.recruitment.model.dto.response.AuthResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.EmailVerificationToken;
import com.sjp.recruitment.model.entity.OauthAccount;
import com.sjp.recruitment.model.entity.OauthRoleSelectionToken;
import com.sjp.recruitment.model.entity.PasswordResetToken;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.EmailVerificationTokenRepository;
import com.sjp.recruitment.repository.OauthAccountRepository;
import com.sjp.recruitment.repository.PasswordResetTokenRepository;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.service.storage.StorageService;
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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
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
    private final OauthAccountRepository oauthAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DtoMapper dtoMapper;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final StorageService storageService;

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

        sendVerificationEmail(savedUser);

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

        boolean adminPortal = request.getPortal() != null
                && "admin".equalsIgnoreCase(request.getPortal().trim());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.role());
        if (isAdmin && !adminPortal) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_PORTAL_REQUIRED",
                    "Tài khoản quản trị vui lòng đăng nhập tại /admin/login"
            );
        }
        if (!isAdmin && adminPortal) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_REQUIRED",
                    "Tài khoản này không có quyền quản trị"
            );
        }

        namedParameterJdbcTemplate.update(
                "UPDATE users SET last_login_at = now(), updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", UUID.fromString(user.id()))
        );

        User entity = userRepository.findByEmail(user.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Khong tim thay nguoi dung"));
        UserResponse response = dtoMapper.toUserResponse(entity);
        return new AuthResponse(response, jwtUtil.generateToken(entity));
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
        bumpTokenVersion(user);
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
        user.setLastLoginAt(LocalDateTime.now());
        User saved = userRepository.save(user);
        linkOauthAccount(saved, token.getProvider(), token.getProviderId(), token.getEmail());
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

    @Transactional
    public Optional<User> findOrLinkOauthUser(String provider, String providerId, String email, String fullName) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedEmail = normalizeEmail(email);
        Optional<OauthAccount> existingOauth = oauthAccountRepository
                .findByProviderAndProviderUserId(normalizedProvider, providerId);
        if (existingOauth.isPresent()) {
            OauthAccount account = existingOauth.get();
            account.setProviderEmail(normalizedEmail);
            User user = account.getUser();
            activateGoogleVerifiedUser(user);
            return Optional.of(userRepository.save(user));
        }

        return userRepository.findByEmail(normalizedEmail)
                .map(user -> {
                    activateGoogleVerifiedUser(user);
                    if (user.getFullName() == null || user.getFullName().isBlank()) {
                        user.setFullName(fullName);
                    }
                    User saved = userRepository.save(user);
                    linkOauthAccount(saved, normalizedProvider, providerId, normalizedEmail);
                    return saved;
                });
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

    @Transactional(readOnly = true)
    public AccountResponse getAccount() {
        return toAccountResponse(getCurrentUser());
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            if (!user.isEmailVerified() && user.getStatusEnum() == User.UserStatus.PENDING_VERIFICATION) {
                sendVerificationEmail(user);
            }
        });
    }

    @Transactional
    public void logout() {
        User user = getCurrentUser();
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();
        requirePasswordLogin(user);
        requireCurrentPassword(user, request.currentPassword());
        if (passwordMatches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "Mat khau moi phai khac mat khau hien tai");
        }
        validatePassword(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    @Transactional
    public AccountResponse updateAvatar(MultipartFile file) {
        User user = getCurrentUser();
        validateAvatarFile(file);
        try {
            StorageService.StoredFile stored = storageService.storeUserAvatar(user.getId(), file);
            user.setAvatarUrl(stored.storageKey());
            return toAccountResponse(userRepository.save(user));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_STORAGE_FAILED", "Khong the luu anh dai dien");
        }
    }

    @Transactional
    public void deactivateAccount(DeactivateAccountRequest request) {
        User user = getCurrentUser();
        requirePasswordLogin(user);
        requireCurrentPassword(user, request.currentPassword());
        user.setStatus(User.UserStatus.SUSPENDED);
        bumpTokenVersion(user);
        userRepository.save(user);
    }

    private void bumpTokenVersion(User user) {
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
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

    private void requirePasswordLogin(User user) {
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_LOGIN_NOT_ENABLED", "Tai khoan nay chua co mat khau noi bo");
        }
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!passwordMatches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CURRENT_PASSWORD_INVALID", "Mat khau hien tai khong dung");
        }
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_FILE_REQUIRED", "Vui long chon anh dai dien");
        }
        if (file.getSize() > 2L * 1024 * 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_FILE_TOO_LARGE", "Anh dai dien khong duoc vuot qua 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png").contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AVATAR_INVALID_TYPE", "Chỉ hỗ trợ ảnh JPG hoặc PNG");
        }
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                throw invalidAvatarBytes();
            }
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidAvatarBytes();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String detectedFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean formatMatches = ("image/jpeg".equals(contentType) && List.of("jpeg", "jpg").contains(detectedFormat))
                        || ("image/png".equals(contentType) && "png".equals(detectedFormat));
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!formatMatches || width < 1 || height < 1 || width > 4096 || height > 4096) {
                    throw invalidAvatarBytes();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidAvatarBytes();
        }
    }

    private ApiException invalidAvatarBytes() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "AVATAR_INVALID_CONTENT",
                "Nội dung ảnh không hợp lệ hoặc kích thước ảnh vượt quá 4096x4096"
        );
    }

    private void activateGoogleVerifiedUser(User user) {
        if (user.getStatusEnum() == User.UserStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Tai khoan da bi khoa");
        }
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setLastLoginAt(LocalDateTime.now());
    }

    private void linkOauthAccount(User user, String provider, String providerId, String providerEmail) {
        String normalizedProvider = normalizeProvider(provider);
        if (oauthAccountRepository.existsByProviderAndProviderUserId(normalizedProvider, providerId)) {
            return;
        }
        OauthAccount account = new OauthAccount();
        account.setUser(user);
        account.setProvider(normalizedProvider);
        account.setProviderUserId(providerId);
        account.setProviderEmail(normalizeEmail(providerEmail));
        oauthAccountRepository.save(account);
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendVerificationEmail(User user) {
        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(hashToken(tokenValue));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(verificationTtlMinutes));
        emailVerificationTokenRepository.save(token);
        emailService.sendVerificationEmail(user.getEmail(), frontendBaseUrl + "/verify-email?token=" + tokenValue);
    }

    private AccountResponse toAccountResponse(User user) {
        return new AccountResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getRoleEnum() == null ? toFrontendRole(user.getRole()) : user.getRoleEnum().name(),
                user.getStatusEnum() == null ? toFrontendStatus(user.getStatus()) : user.getStatusEnum().name(),
                user.isEmailVerified(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
        );
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
