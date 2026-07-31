package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.momo")
public class MomoProperties {
    private boolean enabled = true;
    private String partnerCode = "MOMO";
    private String accessKey = "F8BBA842ECF85";
    private String secretKey = "K951B6PT2tD7wFGf5gct9wDu";
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
    private boolean sandbox = true;
}
