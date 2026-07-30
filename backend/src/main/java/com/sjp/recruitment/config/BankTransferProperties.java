package com.sjp.recruitment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.bank-transfer")
public class BankTransferProperties {
    private boolean enabled = true;
    private String bankName = "VietinBank";
    /** Mã BIN ngân hàng cho VietQR (VietinBank=970415, Vietcombank=970436, BIDV=970418, MB=970422) */
    private String bankCode = "970415";
    private String accountNumber = "100874697360";
    private String accountName = "LUONG CHI DUNG";
    private String branch = "";
    /** Thời hạn thanh toán (phút) */
    private int expireMinutes = 15;
}
