package com.fpt.cinema.exception;

public class BookingOwnershipException extends RuntimeException {

    private final Long bookingId;

    public BookingOwnershipException(Long bookingId) {
        super("Bạn không có quyền truy cập booking này.");
        this.bookingId = bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
