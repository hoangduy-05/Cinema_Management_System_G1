package com.fpt.cinema.dto.response;

import java.math.BigDecimal;

public record AppliedVoucherResponse(
        Long voucherId,
        String voucherCode,
        BigDecimal discountAmount
) {
}
