package com.sjp.recruitment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.BankTransferProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.payment.MomoPaymentGateway;
import com.sjp.recruitment.payment.PayOsPaymentGateway;
import com.sjp.recruitment.payment.VnPayPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceWebhookTest {

    @Mock private AuthService authService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private MomoPaymentGateway momoPaymentGateway;
    @Mock private VnPayPaymentGateway vnPayPaymentGateway;
    @Mock private PayOsPaymentGateway payOsPaymentGateway;
    @Mock private BankTransferProperties bankTransferProperties;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private SystemSettingsService systemSettingsService;

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                authService,
                transactionManager,
                jdbc,
                momoPaymentGateway,
                vnPayPaymentGateway,
                payOsPaymentGateway,
                bankTransferProperties,
                featureLimitService,
                systemSettingsService,
                new ObjectMapper()
        );
    }

    @Test
    void handlePayOsWebhook_ignoresSamplePayloadWithoutOrderCode() {
        Map<String, Object> payload = Map.of("code", "00");
        when(payOsPaymentGateway.extractOrderCode(payload)).thenReturn(null);

        assertDoesNotThrow(() -> billingService.handlePayOsWebhook(payload));
    }

    @Test
    void handlePayOsWebhook_rejectsInvalidSignature() {
        Map<String, Object> payload = Map.of("code", "00");
        when(payOsPaymentGateway.extractOrderCode(payload)).thenReturn(123L);
        when(payOsPaymentGateway.verifyWebhookSignature(payload)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> billingService.handlePayOsWebhook(payload));
        assertEquals("INVALID_SIGNATURE", ex.getCode());
    }

    @Test
    void handleMomoIpn_rejectsInvalidSignature() {
        Map<String, Object> payload = Map.of("orderId", "pay-1");
        when(momoPaymentGateway.verifyIpnSignature(payload)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> billingService.handleMomoIpn(payload));
        assertEquals("INVALID_SIGNATURE", ex.getCode());
    }

    @Test
    void handleMomoIpn_rejectsMissingOrderId() {
        Map<String, Object> payload = new HashMap<>();
        when(momoPaymentGateway.verifyIpnSignature(payload)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> billingService.handleMomoIpn(payload));
        assertEquals("INVALID_ORDER", ex.getCode());
    }

    @Test
    void handleVnPayIpn_returnsFalseWhenSignatureIsInvalid() {
        Map<String, String> params = Map.of("vnp_TxnRef", "abc");
        when(vnPayPaymentGateway.verifySignature(params)).thenReturn(false);

        assertFalse(billingService.handleVnPayIpn(params));
    }
}
