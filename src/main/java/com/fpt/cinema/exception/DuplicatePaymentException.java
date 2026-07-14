package com.fpt.cinema.exception;

public class DuplicatePaymentException extends RuntimeException {

    private final Long bookingId;

    public DuplicatePaymentException(Long bookingId) {
        super("Booking đã có giao dịch thanh toán thành công.");
        this.bookingId = bookingId;
    }

    public Long getBookingId() {
        return bookingId;
    }
}
