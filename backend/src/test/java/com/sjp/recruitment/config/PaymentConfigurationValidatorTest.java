package com.sjp.recruitment.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentConfigurationValidatorTest {

    @Test
    void allowsDisabledGatewaysWithoutCredentials() {
        assertDoesNotThrow(() -> validatorWithDisabledGateways().afterPropertiesSet());
    }

    @Test
    void failsFastWhenEnabledGatewayHasMissingCredentials() {
        MomoProperties momo = new MomoProperties();
        momo.setEnabled(true);
        PaymentConfigurationValidator validator = new PaymentConfigurationValidator(
                momo,
                new VnPayProperties(),
                new PayOsProperties(),
                new BankTransferProperties()
        );

        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    private PaymentConfigurationValidator validatorWithDisabledGateways() {
        return new PaymentConfigurationValidator(
                new MomoProperties(),
                new VnPayProperties(),
                new PayOsProperties(),
                new BankTransferProperties()
        );
    }
}
