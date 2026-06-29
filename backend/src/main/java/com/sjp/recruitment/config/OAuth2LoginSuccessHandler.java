package com.sjp.recruitment.config;

import com.sjp.recruitment.model.entity.OauthRoleSelectionToken;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.UserRepository;
import com.sjp.recruitment.service.AuthService;
import com.sjp.recruitment.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.oauth-role-selection-token-ttl-minutes}")
    private long roleTokenTtlMinutes;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        String providerId = oauthUser.getAttribute("sub");
        String fullName = oauthUser.getAttribute("name");

        if (email == null || providerId == null) {
            getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/login?oauthError=missing_profile");
            return;
        }

        userRepository.findByEmail(email).ifPresentOrElse(existing -> redirectExisting(request, response, existing),
                () -> redirectRoleSelection(request, response, email, providerId, fullName));
    }

    private void redirectExisting(HttpServletRequest request, HttpServletResponse response, User user) {
        try {
            if (!user.isEmailVerified() || user.getStatusEnum() != User.UserStatus.ACTIVE) {
                getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/login?oauthError=account_not_active");
                return;
            }
            String token = URLEncoder.encode(jwtUtil.generateToken(user), StandardCharsets.UTF_8);
            getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/oauth/callback?token=" + token);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void redirectRoleSelection(HttpServletRequest request, HttpServletResponse response, String email, String providerId, String fullName) {
        try {
            OauthRoleSelectionToken token = authService.createOauthRoleSelectionToken(email, "GOOGLE", providerId, fullName, roleTokenTtlMinutes);
            String encoded = URLEncoder.encode(token.getToken(), StandardCharsets.UTF_8);
            getRedirectStrategy().sendRedirect(request, response, frontendBaseUrl + "/select-role?token=" + encoded);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
