package com.fpt.cinema.exception;

public class SeatHoldExpiredException extends RuntimeException {

    private final Long bookingId;

    public SeatHoldExpiredException(Long bookingId) {
        super("Thời gian giữ ghế đã hết hạn.");
        this.bookingId = bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
