package com.fpt.cinema.dto.response;

import java.math.BigDecimal;

public record ComboResponse(
        Long comboId,
        String name,
        String description,
        String imageUrl,
        BigDecimal price
) {
}
