package com.fpt.cinema.event;

import com.fpt.cinema.service.TicketDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingConfirmedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingConfirmedEventListener.class);

    private final TicketDeliveryService ticketDeliveryService;

    public BookingConfirmedEventListener(TicketDeliveryService ticketDeliveryService) {
        this.ticketDeliveryService = ticketDeliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            ticketDeliveryService.deliverTickets(event.bookingId());
        } catch (RuntimeException exception) {
            // Delivery is deliberately isolated from the already committed booking transaction.
            LOGGER.error("Development e-ticket delivery failed for bookingId={}", event.bookingId());
        }
    }
}
