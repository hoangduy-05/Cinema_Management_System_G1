package com.fpt.cinema.exception;

import java.util.Collection;
import java.util.List;

public class SeatUnavailableException extends RuntimeException {

    private final List<Long> showtimeSeatIds;

    public SeatUnavailableException(Long showtimeSeatId) {
        this(List.of(showtimeSeatId));
    }

    public SeatUnavailableException(Collection<Long> showtimeSeatIds) {
        super("Một hoặc nhiều ghế đã được giữ hoặc đã được đặt.");
        this.showtimeSeatIds = List.copyOf(showtimeSeatIds);
    }

    public List<Long> getShowtimeSeatIds() {
        return showtimeSeatIds;
    }
}
