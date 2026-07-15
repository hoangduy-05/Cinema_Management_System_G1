package com.fpt.cinema.service;

public interface BookingExpirationService {

    int expireSeatHeldBookings();

    int expirePendingPaymentBookings();

    int completeFinishedBookings();
}
