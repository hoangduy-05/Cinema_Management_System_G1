package com.fpt.cinema.entity;

import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.exception.InvalidBookingStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public final class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.CREATED, Set.of(BookingStatus.SEAT_HELD),
            BookingStatus.SEAT_HELD, Set.of(
                    BookingStatus.PENDING_PAYMENT,
                    BookingStatus.CANCELLED,
                    BookingStatus.EXPIRED
            ),
            BookingStatus.PENDING_PAYMENT, Set.of(
                    BookingStatus.PENDING_PAYMENT,
                    BookingStatus.CONFIRMED,
                    BookingStatus.CANCELLED,
                    BookingStatus.EXPIRED
            ),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.COMPLETED)
    );

    private static final Set<BookingStatus> TERMINAL_STATUSES = Set.of(
            BookingStatus.COMPLETED,
            BookingStatus.CANCELLED,
            BookingStatus.EXPIRED
    );

    public boolean canTransition(BookingStatus current, BookingStatus target) {
        if (current == null || target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public void validateTransition(
            Long bookingId,
            BookingStatus current,
            BookingStatus target
    ) {
        if (!canTransition(current, target)) {
            throw new InvalidBookingStateTransitionException(bookingId, current, target);
        }
    }

    public void initialize(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        if (booking.getStatus() == null) {
            booking.applyStatus(BookingStatus.CREATED);
            return;
        }
        if (booking.getStatus() != BookingStatus.CREATED) {
            throw new InvalidBookingStateTransitionException(
                    booking.getId(),
                    booking.getStatus(),
                    BookingStatus.CREATED
            );
        }
    }

    public void transition(Booking booking, BookingStatus target) {
        Objects.requireNonNull(booking, "booking must not be null");
        validateTransition(booking.getId(), booking.getStatus(), target);
        booking.applyStatus(target);
    }

    public boolean isTerminal(BookingStatus status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    public Set<BookingStatus> allowedTargets(BookingStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
    }
}
