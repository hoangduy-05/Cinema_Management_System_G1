package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.CheckoutRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.dto.response.BookingSummaryResponse;
import com.fpt.cinema.dto.response.PaymentResponse;

public interface BookingService {

    BookingSummaryResponse getSummary(Long bookingId, String username);

    PaymentResponse checkout(Long bookingId, CheckoutRequest request, String username);

    BookingResponse cancel(Long bookingId, String username);
}
