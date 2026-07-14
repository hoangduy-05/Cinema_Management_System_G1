package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @Size(max = 50, message = "Payment method cannot exceed 50 characters")
        String paymentMethod
) {
}
