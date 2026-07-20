package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.model.dto.request.CompleteOauthRoleRequest;
import com.sjp.recruitment.model.dto.request.LoginRequest;
import com.sjp.recruitment.model.dto.request.RegisterRequest;
import com.sjp.recruitment.model.dto.request.VerifyEmailRequest;
import com.sjp.recruitment.model.dto.response.AuthConfigResponse;
import com.sjp.recruitment.model.dto.response.AuthResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/diagnostic")
    public ResponseEntity<String> diagnostic() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("DB Connection Test: ");
            jdbcTemplate.execute("SELECT 1");
            sb.append("Success\n");
        } catch (Exception e) {
            sb.append("Failed: ").append(e.toString()).append("\n");
        }

        try {
            sb.append("Query companies table: ");
            jdbcTemplate.execute("SELECT count(*) FROM companies");
            sb.append("Exists\n");
        } catch (Exception e) {
            sb.append("Failed: ").append(e.toString()).append("\n");
        }

        try {
            sb.append("Query employers table: ");
            jdbcTemplate.execute("SELECT count(*) FROM employers");
            sb.append("Exists\n");
        } catch (Exception e) {
            sb.append("Failed: ").append(e.toString()).append("\n");
        }

        return ResponseEntity.ok(sb.toString());
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
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUserResponse());
    }
}
