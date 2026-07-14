package com.fpt.cinema.exception;

import com.fpt.cinema.enums.BookingStatus;

public class InvalidBookingStateTransitionException extends RuntimeException {

    private final Long bookingId;
    private final BookingStatus currentStatus;
    private final BookingStatus targetStatus;

    public InvalidBookingStateTransitionException(
            Long bookingId,
            BookingStatus currentStatus,
            BookingStatus targetStatus
    ) {
        super(buildSafeMessage(currentStatus, targetStatus));
        this.bookingId = bookingId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    private static String buildSafeMessage(BookingStatus currentStatus, BookingStatus targetStatus) {
        return "Không thể chuyển trạng thái booking từ " + currentStatus + " sang " + targetStatus + ".";
    }

    public Long getBookingId() {
        return bookingId;
    }

    public BookingStatus getCurrentStatus() {
        return currentStatus;
    }

    public BookingStatus getTargetStatus() {
        return targetStatus;
    }
}
