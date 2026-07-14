package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank(message = "Transaction reference is required")
        @Size(max = 100, message = "Transaction reference cannot exceed 100 characters")
        String transactionReference,

        @NotBlank(message = "Payment status is required")
        @Size(max = 30, message = "Payment status cannot exceed 30 characters")
        String status,

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.00", message = "Payment amount cannot be negative")
        BigDecimal amount,

        @NotBlank(message = "Simulation token is required")
        @Size(max = 255, message = "Simulation token cannot exceed 255 characters")
        String simulationToken
) {
}
