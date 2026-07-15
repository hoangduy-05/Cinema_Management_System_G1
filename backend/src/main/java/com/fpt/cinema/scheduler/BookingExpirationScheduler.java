package com.fpt.cinema.scheduler;

import com.fpt.cinema.service.BookingExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpirationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingExpirationScheduler.class);

    private final BookingExpirationService bookingExpirationService;

    public BookingExpirationScheduler(BookingExpirationService bookingExpirationService) {
        this.bookingExpirationService = bookingExpirationService;
    }

    @Scheduled(fixedDelayString = "${booking.expiration-scan-fixed-delay-ms:30000}")
    public void expireBookings() {
        int heldExpired = bookingExpirationService.expireSeatHeldBookings();
        int paymentExpired = bookingExpirationService.expirePendingPaymentBookings();
        if (heldExpired + paymentExpired > 0) {
            LOGGER.info(
                    "Booking expiration scan completed: seatHeldExpired={}, pendingPaymentExpired={}",
                    heldExpired,
                    paymentExpired
            );
        }
    }

    @Scheduled(fixedDelayString = "${booking.completion-scan-fixed-delay-ms:60000}")
    public void completeBookings() {
        int completed = bookingExpirationService.completeFinishedBookings();
        if (completed > 0) {
            LOGGER.info("Booking completion scan completed: completed={}", completed);
        }
    }
}
