package com.fpt.cinema.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateBookingCombosRequest(
        @NotNull(message = "Combo items are required; use an empty list to remove all combos")
        @Size(max = 50, message = "A booking cannot contain more than 50 different combos")
        List<@Valid BookingComboItemRequest> items
) {
}
