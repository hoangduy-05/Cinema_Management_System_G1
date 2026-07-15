package com.fpt.cinema.dto.response;

import java.math.BigDecimal;

public record BookingComboResponse(
        Long comboId,
        String comboName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
