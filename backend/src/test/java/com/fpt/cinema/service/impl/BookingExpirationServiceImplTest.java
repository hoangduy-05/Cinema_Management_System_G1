package com.fpt.cinema.service.impl;

import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.CinemaOrder;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T03:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 14, 10, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatReservationService seatReservationService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private TicketService ticketService;

    private BookingStateMachine stateMachine;
    private EntityManager entityManager;
    private BookingExpirationServiceImpl service;

    @BeforeEach
    void setUp() {
        stateMachine = new BookingStateMachine();
        entityManager = mock(EntityManager.class);
        service = new BookingExpirationServiceImpl(
                bookingRepository,
                seatReservationService,
                paymentService,
                ticketService,
                stateMachine,
                entityManager,
                CLOCK,
                100
        );
    }

    @Test
    void expiresSeatHeldBookingAndReleasesSeatsAndTickets() {
        Booking booking = bookingAt(BookingStatus.SEAT_HELD);
        booking.setHoldExpiresAt(NOW);
        booking.setAppliedVoucher(new com.fpt.cinema.entity.Voucher());
        when(bookingRepository.findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc(
                BookingStatus.SEAT_HELD, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(1, service.expireSeatHeldBookings());

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(NOW, booking.getExpiredAt());
        assertNull(booking.getAppliedVoucher());
        verify(seatReservationService).releaseHeldSeats(booking);
        verify(ticketService).expireHeldTickets(booking);
        verify(paymentService, never()).expirePendingAttempts(any());
    }

    @Test
    void expiresPendingPaymentAndAllPendingAttempts() {
        Booking booking = bookingAt(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(NOW);
        when(bookingRepository.findByStatusAndPaymentExpiresAtLessThanEqualOrderByIdAsc(
                BookingStatus.PENDING_PAYMENT, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(1, service.expirePendingPaymentBookings());

        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(paymentService).expirePendingAttempts(booking);
        verify(seatReservationService).releaseHeldSeats(booking);
        verify(ticketService).expireHeldTickets(booking);
    }

    @Test
    void schedulerExpirationIsIdempotentWhenCandidateIsSeenAgain() {
        Booking booking = bookingAt(BookingStatus.SEAT_HELD);
        booking.setHoldExpiresAt(NOW.minusSeconds(1));
        when(bookingRepository.findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc(
                BookingStatus.SEAT_HELD, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(1, service.expireSeatHeldBookings());
        assertEquals(0, service.expireSeatHeldBookings());

        verify(seatReservationService, times(1)).releaseHeldSeats(booking);
        verify(ticketService, times(1)).expireHeldTickets(booking);
    }

    @Test
    void skipsCandidateWhoseDeadlineWasExtendedAfterScan() {
        Booking booking = bookingAt(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(NOW.plusSeconds(1));
        when(bookingRepository.findByStatusAndPaymentExpiresAtLessThanEqualOrderByIdAsc(
                BookingStatus.PENDING_PAYMENT, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(0, service.expirePendingPaymentBookings());
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        verify(seatReservationService, never()).releaseHeldSeats(any());
    }

    @Test
    void completesConfirmedBookingOnlyAfterShowtimeEnds() {
        Booking ended = bookingAt(BookingStatus.CONFIRMED);
        ended.getShowtime().setEndTime(NOW);
        when(bookingRepository.findCompletionCandidates(
                BookingStatus.CONFIRMED, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(ended)));
        when(bookingRepository.findByIdForUpdate(ended.getId())).thenReturn(Optional.of(ended));

        assertEquals(1, service.completeFinishedBookings());
        assertEquals(BookingStatus.COMPLETED, ended.getStatus());
        assertEquals(NOW, ended.getCompletedAt());
        assertEquals("COMPLETED", ended.getOrder().getStatus());
    }

    @Test
    void keepsConfirmedBookingBeforeShowtimeEnd() {
        Booking future = bookingAt(BookingStatus.CONFIRMED);
        future.getShowtime().setEndTime(NOW.plusSeconds(1));
        when(bookingRepository.findCompletionCandidates(
                BookingStatus.CONFIRMED, NOW, PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(future)));
        when(bookingRepository.findByIdForUpdate(future.getId())).thenReturn(Optional.of(future));

        assertEquals(0, service.completeFinishedBookings());
        assertEquals(BookingStatus.CONFIRMED, future.getStatus());
    }

    private Booking bookingAt(BookingStatus target) {
        CinemaOrder order = new CinemaOrder();
        order.setStatus(target.name());
        Showtime showtime = new Showtime();
        showtime.setEndTime(NOW.minusMinutes(1));
        Booking booking = new Booking();
        booking.setId(8001L);
        booking.setOrder(order);
        booking.setShowtime(showtime);
        stateMachine.transition(booking, BookingStatus.SEAT_HELD);
        if (target == BookingStatus.SEAT_HELD) {
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        if (target == BookingStatus.PENDING_PAYMENT) {
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.CONFIRMED);
        return booking;
    }
}
