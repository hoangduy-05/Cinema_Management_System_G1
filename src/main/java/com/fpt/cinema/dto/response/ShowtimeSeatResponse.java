package com.fpt.cinema.dto.response;

import java.math.BigDecimal;

public record ShowtimeSeatResponse(
        Long showtimeSeatId,
        Long seatId,
        String seatLabel,
        String seatRow,
        String seatNumber,
        Integer gridRow,
        Integer gridColumn,
        String seatType,
        BigDecimal price,
        String status,
        boolean selectable
) {
}
