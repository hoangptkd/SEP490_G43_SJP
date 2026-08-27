package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoProperties {
    private boolean enabled = false;
    private String partnerCode = "MOMO";
    private String accessKey = "";
    private String secretKey = "";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private boolean sandbox = true;
}
