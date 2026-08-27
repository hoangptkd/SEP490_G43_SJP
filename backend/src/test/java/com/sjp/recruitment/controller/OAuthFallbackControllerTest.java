package com.sjp.recruitment.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthFallbackControllerTest {

    @Mock private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;
    @InjectMocks private OAuthFallbackController controller;

    @Test
    void googleAuthorization_returns404WhenOAuthIsConfigured() throws IOException {
        when(clientRegistrationRepository.getIfAvailable()).thenReturn(mock(ClientRegistrationRepository.class));
        HttpServletResponse response = mock(HttpServletResponse.class);

        controller.googleAuthorization(response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void googleAuthorization_redirectsWhenOAuthNotConfigured() throws IOException {
        when(clientRegistrationRepository.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "http://frontend");
        HttpServletResponse response = mock(HttpServletResponse.class);

        controller.googleAuthorization(response);

        verify(response).sendRedirect("http://frontend/login?oauthError=google_not_configured");
    }
}
