package com.sjp.recruitment.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class OAuthFallbackController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @GetMapping("/oauth2/authorization/google")
    public void googleAuthorization(HttpServletResponse response) throws IOException {
        if (clientRegistrationRepository.getIfAvailable() != null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.sendRedirect(frontendBaseUrl + "/login?oauthError=google_not_configured");
    }
}
