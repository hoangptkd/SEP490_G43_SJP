package com.sjp.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;
import org.springframework.core.env.Environment;

@Configuration
public class GoogleOAuthConfig {

    @Bean
    @Conditional(GoogleOAuthConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(Environment environment) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(environment.getRequiredProperty("app.oauth.google.client-id"))
                .clientSecret(environment.getRequiredProperty("app.oauth.google.client-secret"))
                .scope("openid", "profile", "email")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }

    static class GoogleOAuthConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return isConfigured(environment.getProperty("app.oauth.google.client-id"))
                    && isConfigured(environment.getProperty("app.oauth.google.client-secret"));
        }

        private boolean isConfigured(String value) {
            if (!StringUtils.hasText(value)) {
                return false;
            }
            String normalized = value.trim().toLowerCase();
            return !normalized.contains("placeholder") && !normalized.startsWith("<");
        }
    }
}
