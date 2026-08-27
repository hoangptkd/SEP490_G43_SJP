package com.sjp.recruitment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentConfigurationValidator implements InitializingBean {

    private final MomoProperties momo;
    private final VnPayProperties vnPay;
    private final PayOsProperties payOs;
    private final BankTransferProperties bankTransfer;

    @Override
    public void afterPropertiesSet() {
        if (momo.isEnabled()) {
            require("MOMO_PARTNER_CODE", momo.getPartnerCode());
            require("MOMO_ACCESS_KEY", momo.getAccessKey());
            require("MOMO_SECRET_KEY", momo.getSecretKey());
        }
        if (vnPay.isEnabled()) {
            require("VNPAY_TMN_CODE", vnPay.getTmnCode());
            require("VNPAY_HASH_SECRET", vnPay.getHashSecret());
        }
        if (payOs.isEnabled()) {
            require("PAYOS_CLIENT_ID", payOs.getClientId());
            require("PAYOS_API_KEY", payOs.getApiKey());
            require("PAYOS_CHECKSUM_KEY", payOs.getChecksumKey());
        }
        if (bankTransfer.isEnabled()) {
            require("BANK_TRANSFER_BANK_NAME", bankTransfer.getBankName());
            require("BANK_TRANSFER_BANK_CODE", bankTransfer.getBankCode());
            require("BANK_TRANSFER_ACCOUNT_NUMBER", bankTransfer.getAccountNumber());
            require("BANK_TRANSFER_ACCOUNT_NAME", bankTransfer.getAccountName());
        }
    }

    private void require(String environmentName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required when its payment gateway is enabled");
        }
    }
}
