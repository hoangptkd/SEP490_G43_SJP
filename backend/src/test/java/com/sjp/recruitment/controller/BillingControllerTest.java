package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.CheckoutRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock private BillingService billingService;
    @InjectMocks private BillingController billingController;

    @Test
    void listPlans_delegatesToService() {
        List<PlanCatalogResponse> expected = List.of(mock(PlanCatalogResponse.class));
        when(billingService.listAvailablePlans()).thenReturn(expected);

        ResponseEntity<List<PlanCatalogResponse>> response = billingController.listPlans();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
    }

    @Test
    void getMySubscription_delegatesToService() {
        UserSubscriptionResponse expected = mock(UserSubscriptionResponse.class);
        when(billingService.getMySubscription()).thenReturn(expected);

        ResponseEntity<UserSubscriptionResponse> response = billingController.getMySubscription();

        assertSame(expected, response.getBody());
    }

    @Test
    void checkout_delegatesToService() {
        CheckoutRequest request = mock(CheckoutRequest.class);
        CheckoutResponse expected = mock(CheckoutResponse.class);
        when(billingService.checkout(request)).thenReturn(expected);

        ResponseEntity<CheckoutResponse> response = billingController.checkout(request);

        assertSame(expected, response.getBody());
    }

    @Test
    void getPaymentStatus_delegatesToService() {
        PaymentStatusResponse expected = mock(PaymentStatusResponse.class);
        when(billingService.getPaymentStatus("pay-1")).thenReturn(expected);

        ResponseEntity<PaymentStatusResponse> response = billingController.getPaymentStatus("pay-1");

        assertSame(expected, response.getBody());
    }

    @Test
    void getBankTransfer_delegatesToService() {
        BankTransferInfo expected = mock(BankTransferInfo.class);
        when(billingService.getBankTransferCheckout("pay-1")).thenReturn(expected);

        ResponseEntity<BankTransferInfo> response = billingController.getBankTransfer("pay-1");

        assertSame(expected, response.getBody());
    }

    @Test
    void getMyPaymentHistory_delegatesToService() {
        List<PaymentStatusResponse> expected = List.of(mock(PaymentStatusResponse.class));
        when(billingService.getMyPaymentHistory()).thenReturn(expected);

        ResponseEntity<List<PaymentStatusResponse>> response = billingController.getMyPaymentHistory();

        assertSame(expected, response.getBody());
    }
}
