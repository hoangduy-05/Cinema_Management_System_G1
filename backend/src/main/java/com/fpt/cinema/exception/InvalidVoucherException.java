package com.fpt.cinema.exception;

public class InvalidVoucherException extends RuntimeException {

    public InvalidVoucherException() {
        this("Voucher không hợp lệ hoặc không thể áp dụng.");
    }

    public InvalidVoucherException(String safeMessage) {
        super(safeMessage);
    }
}
