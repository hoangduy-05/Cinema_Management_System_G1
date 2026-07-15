package com.fpt.cinema.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyVoucherRequest(
        @NotBlank(message = "Voucher code is required")
        @Size(max = 50, message = "Voucher code cannot exceed 50 characters")
        String voucherCode
) {
}
