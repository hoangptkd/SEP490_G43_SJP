package com.sjp.recruitment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.storage.avatar-upload-dir:uploads/avatars}")
    private String avatarUploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:5174"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarLocation = "file:" + Path.of(avatarUploadDir).toAbsolutePath().toString().replace('\\', '/');
        if (!avatarLocation.endsWith("/")) {
            avatarLocation += "/";
        }
        registry.addResourceHandler("/public/avatars/**")
                .addResourceLocations(avatarLocation);
    }
}
