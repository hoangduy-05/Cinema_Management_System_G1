package com.fpt.cinema.exception;

public class BookingNotFoundException extends RuntimeException {

    private final Long bookingId;

    public BookingNotFoundException(Long bookingId) {
        super("Không tìm thấy booking được yêu cầu.");
        this.bookingId = bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
