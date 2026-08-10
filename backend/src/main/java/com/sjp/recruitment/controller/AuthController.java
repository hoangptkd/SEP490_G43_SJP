package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.request.ChangePasswordRequest;
import com.sjp.recruitment.model.dto.request.CompleteOauthRoleRequest;
import com.sjp.recruitment.model.dto.request.DeactivateAccountRequest;
import com.sjp.recruitment.model.dto.request.ForgotPasswordRequest;
import com.sjp.recruitment.model.dto.request.LoginRequest;
import com.sjp.recruitment.model.dto.request.RegisterRequest;
import com.sjp.recruitment.model.dto.request.ResetPasswordRequest;
import com.sjp.recruitment.model.dto.request.VerifyEmailRequest;
import com.sjp.recruitment.model.dto.response.AccountResponse;
import com.sjp.recruitment.model.dto.response.AuthConfigResponse;
import com.sjp.recruitment.model.dto.response.AuthResponse;
import com.sjp.recruitment.model.dto.response.MessageResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> config() {
        return ResponseEntity.ok(new AuthConfigResponse(clientRegistrationRepository.getIfAvailable() != null));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(new MessageResponse("Neu email ton tai, huong dan dat lai mat khau da duoc gui"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Mat khau da duoc cap nhat"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<UserResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @PostMapping("/oauth/complete-role")
    public ResponseEntity<AuthResponse> completeOauthRole(@Valid @RequestBody CompleteOauthRoleRequest request) {
        return ResponseEntity.ok(authService.completeOauthRole(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUserResponse());
    }

    @GetMapping("/account")
    public ResponseEntity<AccountResponse> getAccount() {
        return ResponseEntity.ok(authService.getAccount());
    }

    @PutMapping("/account/password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(new MessageResponse("Mat khau da duoc cap nhat"));
    }

    @PutMapping(value = "/account/avatar", consumes = "multipart/form-data")
    public ResponseEntity<AccountResponse> updateAvatar(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(authService.updateAvatar(file));
    }

    @PostMapping("/account/deactivate")
    public ResponseEntity<MessageResponse> deactivateAccount(@Valid @RequestBody DeactivateAccountRequest request) {
        authService.deactivateAccount(request);
        return ResponseEntity.ok(new MessageResponse("Tai khoan da duoc vo hieu hoa"));
    }
}
