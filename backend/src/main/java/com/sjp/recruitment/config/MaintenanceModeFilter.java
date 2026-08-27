package com.sjp.recruitment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.service.AuthService;
import com.sjp.recruitment.service.SystemSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final SystemSettingsService systemSettingsService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizedPath(request);
        return path.startsWith("/auth/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/actuator/")
                || path.equals("/settings/public")
                || path.startsWith("/admin/")
                || path.startsWith("/payments/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!systemSettingsService.isMaintenanceMode()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            User user = authService.getCurrentUser();
            if (user != null && user.getRoleEnum() == User.UserRole.ADMIN) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception ignored) {
            // anonymous / invalid → block
        }

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", "MAINTENANCE_MODE",
                "message", "Hệ thống đang bảo trì. Vui lòng quay lại sau."
        ));
    }

    private String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String relative = uri.startsWith(context) ? uri.substring(context.length()) : uri;
        if (!relative.startsWith("/")) {
            relative = "/" + relative;
        }
        return relative;
    }
}
