package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.CheckoutRequest;
import com.fpt.cinema.dto.response.BookingHistoryItemResponse;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.dto.response.BookingSummaryResponse;
import com.fpt.cinema.dto.response.PageResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.enums.BookingStatus;

public interface BookingService {

    BookingSummaryResponse getSummary(Long bookingId, String username);

    PageResponse<BookingHistoryItemResponse> getMyBookingHistory(
            String username,
            BookingStatus status,
            int page,
            int size
    );

    PaymentResponse checkout(Long bookingId, CheckoutRequest request, String username);

    BookingResponse cancel(Long bookingId, String username);
}
