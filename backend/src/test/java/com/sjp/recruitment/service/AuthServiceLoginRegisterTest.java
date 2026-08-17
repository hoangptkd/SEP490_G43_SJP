package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.LoginRequest;
import com.sjp.recruitment.model.dto.request.RegisterRequest;
import com.sjp.recruitment.model.dto.response.AuthResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.CompanyIndustryRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmailVerificationTokenRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.OauthAccountRepository;
import com.sjp.recruitment.repository.PasswordResetTokenRepository;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.service.storage.StorageService;
import com.sjp.recruitment.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginRegisterTest {

    @Mock private UserRepository userRepository;
    @Mock private CandidateProfileRepository candidateProfileRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private CompanyIndustryRepository companyIndustryRepository;
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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "verificationTtlMinutes", 30L);
        ReflectionTestUtils.setField(authService, "passwordResetTtlMinutes", 30L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_rejectsExistingEmail() {
        RegisterRequest request = candidateRegister();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals("EMAIL_EXISTS", ex.getCode());
    }

    @Test
    void register_rejectsWeakPassword() {
        RegisterRequest request = candidateRegister();
        request.setPassword("weak");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals("WEAK_PASSWORD", ex.getCode());
    }

    @Test
    void register_requiresEmployerFullNameAndPhone() {
        RegisterRequest request = candidateRegister();
        request.setRole(User.UserRole.EMPLOYER);
        request.setFullName(" ");
        request.setPhone(null);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals("INVALID_INPUT", ex.getCode());
    }

    @Test
    void register_createsCandidateAndSendsVerificationEmail() {
        RegisterRequest request = candidateRegister();
        UserResponse mapped = new UserResponse(UUID.randomUUID().toString(), request.getEmail(), "CANDIDATE", "PENDING_VERIFICATION", false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(candidateProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(candidateProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toUserResponse(any(User.class))).thenReturn(mapped);

        AuthResponse response = authService.register(request);

        assertSame(mapped, response.user());
        assertNull(response.token());
        verify(emailService).sendVerificationEmail(eq(request.getEmail()), anyString());
        verify(emailVerificationTokenRepository).save(any());
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(login("missing@srp.test", "Password1")));
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void login_rejectsWrongPassword() throws Exception {
        stubRemoteUser("job_seeker", "active", true);
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(login("candidate@srp.test", "Password1")));
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void login_rejectsUnverifiedEmail() throws Exception {
        stubRemoteUser("job_seeker", "inactive", false);
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(login("candidate@srp.test", "Password1")));
        assertEquals("EMAIL_NOT_VERIFIED", ex.getCode());
    }

    @Test
    void login_requiresAdminPortalForAdmin() throws Exception {
        stubRemoteUser("admin", "active", true);
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(login("admin@srp.test", "Password1")));
        assertEquals("ADMIN_PORTAL_REQUIRED", ex.getCode());
    }

    @Test
    void login_rejectsNonAdminOnAdminPortal() throws Exception {
        stubRemoteUser("job_seeker", "active", true);
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
        LoginRequest request = login("candidate@srp.test", "Password1");
        request.setPortal("admin");

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals("ADMIN_REQUIRED", ex.getCode());
    }

    @Test
    void login_returnsJwtForVerifiedCandidate() throws Exception {
        UUID userId = stubRemoteUser("job_seeker", "active", true);
        User entity = new User();
        entity.setId(userId);
        entity.setEmail("candidate@srp.test");
        UserResponse mapped = new UserResponse(userId.toString(), "candidate@srp.test", "CANDIDATE", "ACTIVE", true);
        when(passwordEncoder.matches("Password1", "hash")).thenReturn(true);
        when(userRepository.findByEmail("candidate@srp.test")).thenReturn(Optional.of(entity));
        when(dtoMapper.toUserResponse(entity)).thenReturn(mapped);
        when(jwtUtil.generateToken(entity)).thenReturn("jwt-token");

        AuthResponse response = authService.login(login("candidate@srp.test", "Password1"));

        assertEquals("jwt-token", response.token());
        assertSame(mapped, response.user());
        verify(namedParameterJdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void getCurrentUser_requiresAuthentication() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.getCurrentUser());
        assertEquals("Unauthenticated", ex.getMessage());
    }

    @Test
    void getCurrentUser_returnsUserPrincipal() {
        User user = new User();
        user.setEmail("candidate@srp.test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );

        assertSame(user, authService.getCurrentUser());
    }

    @Test
    void getCurrentUserResponse_returnsMappedUser() {
        User user = new User();
        UserResponse mapped = new UserResponse("1", "a@b.c", "CANDIDATE", "ACTIVE", true);
        when(dtoMapper.toUserResponse(user)).thenReturn(mapped);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );

        assertSame(mapped, authService.getCurrentUserResponse());
    }

    @SuppressWarnings("unchecked")
    private UUID stubRemoteUser(String role, String status, boolean verified) throws Exception {
        UUID userId = UUID.randomUUID();
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(userId.toString());
                    when(rs.getString("email")).thenReturn(role.equals("admin") ? "admin@srp.test" : "candidate@srp.test");
                    when(rs.getString("password_hash")).thenReturn("hash");
                    when(rs.getString("role")).thenReturn(role);
                    when(rs.getString("status")).thenReturn(status);
                    when(rs.getTimestamp("email_verified_at"))
                            .thenReturn(verified ? Timestamp.valueOf(LocalDateTime.now()) : null);
                    return List.of(mapper.mapRow(rs, 0));
                });
        return userId;
    }

    private static RegisterRequest candidateRegister() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("candidate@srp.test");
        request.setPassword("Password1");
        request.setRole(User.UserRole.CANDIDATE);
        request.setFullName("Nguyen Van A");
        return request;
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
