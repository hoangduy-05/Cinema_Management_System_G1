package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingComboItemRequest(
        @NotNull(message = "Combo is required")
        @Positive(message = "Combo ID must be positive")
        Long comboId,

        @NotNull(message = "Combo quantity is required")
        @Positive(message = "Combo quantity must be positive")
        Integer quantity
) {
}
