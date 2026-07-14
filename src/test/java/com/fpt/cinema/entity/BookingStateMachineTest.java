package com.fpt.cinema.entity;

import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.exception.InvalidBookingStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingStateMachineTest {

    private static final Map<BookingStatus, Set<BookingStatus>> EXPECTED = Map.of(
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

    private final BookingStateMachine stateMachine = new BookingStateMachine();

    @Test
    void allowsExactlyTheDeclaredTransitions() {
        for (BookingStatus current : BookingStatus.values()) {
            for (BookingStatus target : BookingStatus.values()) {
                assertThat(stateMachine.canTransition(current, target))
                        .as("%s -> %s", current, target)
                        .isEqualTo(EXPECTED.getOrDefault(current, Set.of()).contains(target));
            }
        }
    }

    @Test
    void terminalStatesCannotTransition() {
        for (BookingStatus terminal : Set.of(
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED,
                BookingStatus.EXPIRED
        )) {
            assertThat(stateMachine.isTerminal(terminal)).isTrue();
            assertThat(stateMachine.allowedTargets(terminal)).isEmpty();
        }
    }

    @Test
    void transitionMutatesBookingOnlyWhenAllowed() {
        Booking booking = new Booking();

        stateMachine.transition(booking, BookingStatus.SEAT_HELD);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SEAT_HELD);
    }

    @Test
    void invalidTransitionIncludesSafeContext() {
        Booking booking = new Booking();

        assertThatThrownBy(() -> stateMachine.transition(booking, BookingStatus.CONFIRMED))
                .isInstanceOf(InvalidBookingStateTransitionException.class)
                .satisfies(exception -> {
                    InvalidBookingStateTransitionException transitionException =
                            (InvalidBookingStateTransitionException) exception;
                    assertThat(transitionException.getCurrentStatus()).isEqualTo(BookingStatus.CREATED);
                    assertThat(transitionException.getTargetStatus()).isEqualTo(BookingStatus.CONFIRMED);
                    assertThat(transitionException.getMessage()).doesNotContain("null pointer");
                });
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CREATED);
    }
}
