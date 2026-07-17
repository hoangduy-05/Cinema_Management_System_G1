package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.PaymentCallbackRequest;
import com.fpt.cinema.dto.response.PaymentCallbackResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.entity.Booking;

public interface PaymentService {

    PaymentResponse createOrReusePayment(Booking booking, String paymentMethod);

    PaymentResponse createPayment(Long bookingId, String username, String paymentMethod);

    PaymentResponse getLatestPayment(Long bookingId, String username);

    PaymentResponse retryPayment(Long bookingId, String username, String paymentMethod);

    PaymentResponse retryPaymentAttempt(Long paymentId, String username, String paymentMethod);

    PaymentCallbackResponse confirmBrowserPayment(Long paymentId, String username);

    PaymentCallbackResponse processCallback(PaymentCallbackRequest request);

    void expirePendingAttempts(Booking booking);
}
