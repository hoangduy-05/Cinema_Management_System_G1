package com.fpt.cinema.dto.response;

import java.math.BigDecimal;

public record BookingSeatResponse(
        Long showtimeSeatId,
        Long seatId,
        String seatLabel,
        String seatRow,
        String seatNumber,
        String seatType,
        BigDecimal price
) {
}
