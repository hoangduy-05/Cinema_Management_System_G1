package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotNull(message = "Booking is required")
        @Positive(message = "Booking ID must be positive")
        Long bookingId,

        @Size(max = 50, message = "Payment method cannot exceed 50 characters")
        String paymentMethod
) {
}
