package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.ApplyVoucherRequest;
import com.fpt.cinema.dto.request.UpdateBookingCombosRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.entity.Booking;

import java.time.LocalDateTime;

public interface BookingPricingService {

    BookingResponse updateCombos(
            Long bookingId,
            UpdateBookingCombosRequest request,
            String username
    );

    BookingResponse applyVoucher(
            Long bookingId,
            ApplyVoucherRequest request,
            String username
    );

    BookingResponse removeVoucher(Long bookingId, String username);

    /**
     * Rebuilds all monetary values from current database prices. The booking must
     * already be locked by the caller.
     */
    void recalculateForCheckout(Booking booking, LocalDateTime now);
}
