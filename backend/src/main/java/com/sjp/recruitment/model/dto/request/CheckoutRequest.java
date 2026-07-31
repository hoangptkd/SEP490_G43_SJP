package com.sjp.recruitment.model.dto.request;

public record CheckoutRequest(
        String planId,
        String paymentMethod
) {
}
