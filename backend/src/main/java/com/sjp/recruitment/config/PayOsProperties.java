package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.payos")
public class PayOsProperties {
    private boolean enabled = true;
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String endpoint = "https://api-merchant.payos.vn";
}
