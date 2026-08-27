package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminPlanUpdateRequest;
import com.sjp.recruitment.model.dto.request.SubscriptionActionRequest;
import com.sjp.recruitment.model.dto.response.*;
import com.sjp.recruitment.service.AdminOpsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBillingControllerTest {

    @Mock private AdminOpsService adminOpsService;
    @InjectMocks private AdminBillingController controller;

    @Test
    void listPlans_delegatesToService() {
        List<AdminPlanResponse> expected = List.of(mock(AdminPlanResponse.class));
        when(adminOpsService.listPlans("all")).thenReturn(expected);
        assertSame(expected, controller.listPlans("all").getBody());
    }

    @Test
    void createPlan_delegatesToService() {
        AdminPlanUpdateRequest request = mock(AdminPlanUpdateRequest.class);
        AdminPlanResponse expected = mock(AdminPlanResponse.class);
        when(adminOpsService.createPlan(request)).thenReturn(expected);
        assertSame(expected, controller.createPlan(request).getBody());
    }

    @Test
    void updatePlan_delegatesToService() {
        AdminPlanUpdateRequest request = mock(AdminPlanUpdateRequest.class);
        AdminPlanResponse expected = mock(AdminPlanResponse.class);
        when(adminOpsService.updatePlan("p-1", request)).thenReturn(expected);
        assertSame(expected, controller.updatePlan("p-1", request).getBody());
    }

    @Test
    void deletePlan_delegatesAndReturnsNoContent() {
        assertEquals(HttpStatus.NO_CONTENT, controller.deletePlan("p-1").getStatusCode());
        verify(adminOpsService).deletePlan("p-1");
    }

    @Test
    void listSubscriptions_delegatesToService() {
        List<AdminSubscriptionResponse> expected = List.of(mock(AdminSubscriptionResponse.class));
        when(adminOpsService.listSubscriptions("all")).thenReturn(expected);
        assertSame(expected, controller.listSubscriptions("all").getBody());
    }

    @Test
    void cancelSubscription_delegatesToService() {
        SubscriptionActionRequest request = mock(SubscriptionActionRequest.class);
        AdminSubscriptionResponse expected = mock(AdminSubscriptionResponse.class);
        when(adminOpsService.cancelSubscription("s-1", request)).thenReturn(expected);
        assertSame(expected, controller.cancelSubscription("s-1", request).getBody());
    }

    @Test
    void activateSubscription_delegatesToService() {
        AdminSubscriptionResponse expected = mock(AdminSubscriptionResponse.class);
        when(adminOpsService.activateSubscription("s-1")).thenReturn(expected);
        assertSame(expected, controller.activateSubscription("s-1").getBody());
    }

    @Test
    void listPayments_delegatesToService() {
        List<AdminPaymentResponse> expected = List.of(mock(AdminPaymentResponse.class));
        when(adminOpsService.listPayments("all")).thenReturn(expected);
        assertSame(expected, controller.listPayments("all").getBody());
    }

    @Test
    void confirmPayment_delegatesToService() {
        AdminPaymentResponse expected = mock(AdminPaymentResponse.class);
        when(adminOpsService.confirmBankPayment("pay-1")).thenReturn(expected);
        assertSame(expected, controller.confirmPayment("pay-1").getBody());
    }

    @Test
    void getRevenue_delegatesToService() {
        AdminRevenueSummaryResponse expected = mock(AdminRevenueSummaryResponse.class);
        when(adminOpsService.getRevenueSummary()).thenReturn(expected);
        assertSame(expected, controller.getRevenue().getBody());
    }
}
