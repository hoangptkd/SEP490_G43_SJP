package com.sjp.recruitment.model.dto.response;

public record CheckoutResponse(
        String paymentId,
        String subscriptionId,
        String status,
        String payUrl,
        String message,
        String paymentMethod,
        BankTransferInfo bankTransfer
) {
    public static CheckoutResponse redirect(String paymentId, String subscriptionId, String status,
                                            String payUrl, String message, String paymentMethod) {
        return new CheckoutResponse(paymentId, subscriptionId, status, payUrl, message, paymentMethod, null);
    }

    public static CheckoutResponse bank(String paymentId, String subscriptionId, String message,
                                        BankTransferInfo bankTransfer) {
        return new CheckoutResponse(paymentId, subscriptionId, "pending", null, message, "bank_transfer", bankTransfer);
    }

    public static CheckoutResponse paid(String paymentId, String subscriptionId, String message) {
        return new CheckoutResponse(paymentId, subscriptionId, "paid", null, message, "free", null);
    }
}
