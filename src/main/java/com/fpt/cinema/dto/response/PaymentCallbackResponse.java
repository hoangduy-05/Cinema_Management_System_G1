package com.fpt.cinema.dto.response;

public record PaymentCallbackResponse(
        Long bookingId,
        Long paymentId,
        String transactionReference,
        String paymentStatus,
        String bookingStatus,
        boolean successful,
        boolean retryAllowed,
        String message
) {
}
