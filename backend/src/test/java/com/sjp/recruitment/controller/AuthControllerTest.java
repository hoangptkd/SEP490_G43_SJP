package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.*;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.AuthRateLimiter;
import com.sjp.recruitment.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private AuthRateLimiter authRateLimiter;
    @Mock private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;
    @Mock private HttpServletRequest servletRequest;

    @InjectMocks private AuthController authController;

    @Test
    void config_returnsGoogleOAuthAvailability() {
        when(clientRegistrationRepository.getIfAvailable()).thenReturn(null);
        ResponseEntity<AuthConfigResponse> response = authController.config();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().googleOAuthEnabled());
    }

    @Test
    void config_returnsTrueWhenGoogleOAuthAvailable() {
        when(clientRegistrationRepository.getIfAvailable()).thenReturn(mock(ClientRegistrationRepository.class));
        ResponseEntity<AuthConfigResponse> response = authController.config();
        assertTrue(response.getBody().googleOAuthEnabled());
    }

    @Test
    void register_checksRateLimitAndDelegates() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@srp.test");
        AuthResponse expected = new AuthResponse(null, null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(authService.register(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.register(request, servletRequest);

        verify(authRateLimiter).check(AuthRateLimiter.Operation.REGISTER, "test@srp.test", "127.0.0.1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void login_checksRateLimitAndDelegates() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@srp.test");
        AuthResponse expected = new AuthResponse(null, "jwt");
        when(servletRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.login(request, servletRequest);

        verify(authRateLimiter).check(AuthRateLimiter.Operation.LOGIN, "user@srp.test", "10.0.0.1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void forgotPassword_delegatesAndReturnsMessage() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@srp.test");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<MessageResponse> response = authController.forgotPassword(request, servletRequest);

        verify(authRateLimiter).check(AuthRateLimiter.Operation.FORGOT_PASSWORD, "user@srp.test", "127.0.0.1");
        verify(authService).forgotPassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void resetPassword_delegatesAndReturnsMessage() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "NewPass1");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<MessageResponse> response = authController.resetPassword(request, servletRequest);

        verify(authRateLimiter).check(AuthRateLimiter.Operation.RESET_PASSWORD, "token-123", "127.0.0.1");
        verify(authService).resetPassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void verifyEmail_delegatesToService() {
        UserResponse expected = mock(UserResponse.class);
        when(authService.verifyEmail("token-abc")).thenReturn(expected);

        ResponseEntity<UserResponse> response = authController.verifyEmail(new VerifyEmailRequest("token-abc"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void resendVerification_checksRateLimitAndDelegates() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@srp.test");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<MessageResponse> response = authController.resendVerification(request, servletRequest);

        verify(authRateLimiter).check(AuthRateLimiter.Operation.RESEND_VERIFICATION, "user@srp.test", "127.0.0.1");
        verify(authService).resendVerification("user@srp.test");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void completeOauthRole_delegatesToService() {
        CompleteOauthRoleRequest request = mock(CompleteOauthRoleRequest.class);
        AuthResponse expected = new AuthResponse(null, "jwt");
        when(authService.completeOauthRole(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = authController.completeOauthRole(request);

        assertSame(expected, response.getBody());
    }

    @Test
    void logout_delegatesToServiceAndReturnsOk() {
        ResponseEntity<Void> response = authController.logout();

        verify(authService).logout();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getCurrentUser_returnsUserResponse() {
        UserResponse expected = mock(UserResponse.class);
        when(authService.getCurrentUserResponse()).thenReturn(expected);

        ResponseEntity<UserResponse> response = authController.getCurrentUser();

        assertSame(expected, response.getBody());
    }

    @Test
    void getAccount_returnsAccountResponse() {
        AccountResponse expected = mock(AccountResponse.class);
        when(authService.getAccount()).thenReturn(expected);

        ResponseEntity<AccountResponse> response = authController.getAccount();

        assertSame(expected, response.getBody());
    }

    @Test
    void changePassword_delegatesAndReturnsMessage() {
        ChangePasswordRequest request = mock(ChangePasswordRequest.class);

        ResponseEntity<MessageResponse> response = authController.changePassword(request);

        verify(authService).changePassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateAvatar_delegatesAndReturnsAccount() {
        MultipartFile file = mock(MultipartFile.class);
        AccountResponse expected = mock(AccountResponse.class);
        when(authService.updateAvatar(file)).thenReturn(expected);

        ResponseEntity<AccountResponse> response = authController.updateAvatar(file);

        assertSame(expected, response.getBody());
    }

    @Test
    void deactivateAccount_delegatesAndReturnsMessage() {
        DeactivateAccountRequest request = mock(DeactivateAccountRequest.class);

        ResponseEntity<MessageResponse> response = authController.deactivateAccount(request);

        verify(authService).deactivateAccount(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
