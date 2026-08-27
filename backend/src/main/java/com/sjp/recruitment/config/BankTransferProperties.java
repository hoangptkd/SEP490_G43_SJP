package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.bank-transfer")
public class BankTransferProperties {
    private boolean enabled = false;
    private String bankName = "";
    /** Mã BIN ngân hàng cho VietQR (VietinBank=970415, Vietcombank=970436, BIDV=970418, MB=970422) */
    private String bankCode = "";
    private String accountNumber = "";
    private String accountName = "";
    private String branch = "";
    /** Thời hạn thanh toán (phút) */
    private int expireMinutes = 15;
}
