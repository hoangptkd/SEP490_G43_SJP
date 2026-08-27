package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth-rate-limit")
public class AuthRateLimitProperties {
    private boolean enabled = true;
    private int windowSeconds = 60;
    private int loginRequests = 10;
    private int registerRequests = 5;
    private int forgotPasswordRequests = 5;
    private int resetPasswordRequests = 10;
    private int resendVerificationRequests = 3;
}
