package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long bookingId,
        Long paymentId,
        String transactionReference,
        BigDecimal amount,
        String paymentMethod,
        String paymentStatus,
        String bookingStatus,
        String paymentUrl,
        LocalDateTime paymentExpiresAt,
        boolean retryAllowed
) {
}
