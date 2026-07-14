package com.fpt.cinema.exception;

public class PaymentVerificationException extends RuntimeException {

    public PaymentVerificationException() {
        this("Không thể xác minh giao dịch thanh toán.");
    }

    public PaymentVerificationException(String safeMessage) {
        super(safeMessage);
    }
}
