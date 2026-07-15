package com.fpt.cinema.service;

import com.fpt.cinema.dto.request.HoldSeatsRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.entity.Booking;

public interface SeatReservationService {

    BookingResponse holdSeats(HoldSeatsRequest request, String username);

    /**
     * Releases only rows that are still HELD and still owned by {@code booking}.
     * The caller is responsible for owning the surrounding transaction.
     */
    void releaseHeldSeats(Booking booking);
}
