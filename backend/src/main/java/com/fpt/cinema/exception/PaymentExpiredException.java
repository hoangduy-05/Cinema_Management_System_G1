package com.fpt.cinema.exception;

public class PaymentExpiredException extends RuntimeException {

    private final Long paymentId;

    public PaymentExpiredException(Long paymentId) {
        super("Thời hạn thanh toán đã kết thúc.");
        this.paymentId = paymentId;
    }

    public Long getPaymentId() {
        return paymentId;
    }
}
