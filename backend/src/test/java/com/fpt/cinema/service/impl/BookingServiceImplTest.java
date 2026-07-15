package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.CheckoutRequest;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.CinemaOrder;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.Room;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Voucher;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.exception.InvalidBookingStateTransitionException;
import com.fpt.cinema.exception.PaymentExpiredException;
import com.fpt.cinema.exception.SeatHoldExpiredException;
import com.fpt.cinema.mapper.BookingMapper;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.OrderComboRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.service.BookingPricingService;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T03:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 14, 10, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private OrderComboRepository orderComboRepository;
    @Mock
    private BookingPricingService bookingPricingService;
    @Mock
    private SeatReservationService seatReservationService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private TicketService ticketService;
    @Mock
    private BookingMapper bookingMapper;

    private BookingStateMachine stateMachine;
    private BookingServiceImpl service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        stateMachine = new BookingStateMachine();
        service = new BookingServiceImpl(
                bookingRepository,
                customerRepository,
                showtimeSeatRepository,
                ticketRepository,
                orderComboRepository,
                stateMachine,
                bookingPricingService,
                seatReservationService,
                paymentService,
                ticketService,
                bookingMapper,
                CLOCK
        );
        customer = activeCustomer();
    }

    @Test
    void checkoutRecalculatesTotalsAndMovesLiveHoldToPendingPayment() {
        Booking booking = bookingAt(BookingStatus.SEAT_HELD);
        ShowtimeSeat heldSeat = heldSeatOwnedBy(booking);
        PaymentResponse expected = paymentResponse(booking.getId());
        stubAuthenticatedBooking(booking);
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId()))
                .thenReturn(List.of(heldSeat));
        when(paymentService.createOrReusePayment(booking, "VNPAY")).thenReturn(expected);

        PaymentResponse result = service.checkout(booking.getId(), new CheckoutRequest("vnpay"), "customer");

        assertSame(expected, result);
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertEquals("PENDING_PAYMENT", booking.getOrder().getStatus());
        assertEquals("PENDING", booking.getOrder().getPaymentStatus());
        assertEquals(NOW, booking.getUpdatedAt());
        verify(bookingPricingService).recalculateForCheckout(booking, NOW);
        verify(paymentService).createOrReusePayment(booking, "VNPAY");
    }

    @Test
    void repeatedCheckoutReusesPaymentWithoutRepricingOrRelockingSeats() {
        Booking booking = bookingAt(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(NOW.plusMinutes(5));
        PaymentResponse expected = paymentResponse(booking.getId());
        stubAuthenticatedBooking(booking);
        when(paymentService.createOrReusePayment(booking, "SIMULATED")).thenReturn(expected);

        assertSame(expected, service.checkout(booking.getId(), null, "customer"));

        verify(bookingPricingService, never()).recalculateForCheckout(any(), any());
        verify(showtimeSeatRepository, never()).findAllByBookingIdForUpdate(any());
        verify(paymentService).createOrReusePayment(booking, "SIMULATED");
    }

    @Test
    void checkoutRejectsExpiredSeatHold() {
        Booking booking = bookingAt(BookingStatus.SEAT_HELD);
        booking.setHoldExpiresAt(NOW);
        stubAuthenticatedBooking(booking);

        assertThrows(
                SeatHoldExpiredException.class,
                () -> service.checkout(booking.getId(), null, "customer")
        );
        verify(bookingPricingService, never()).recalculateForCheckout(any(), any());
        verify(paymentService, never()).createOrReusePayment(any(), any());
    }

    @Test
    void repeatedCheckoutRejectsExpiredPaymentWindow() {
        Booking booking = bookingAt(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(NOW);
        stubAuthenticatedBooking(booking);

        assertThrows(
                PaymentExpiredException.class,
                () -> service.checkout(booking.getId(), null, "customer")
        );
        verify(paymentService, never()).createOrReusePayment(any(), any());
    }

    @Test
    void cancelsSeatHeldBookingAndReleasesOnlyThroughDomainServices() {
        Booking booking = bookingAt(BookingStatus.SEAT_HELD);
        Voucher voucher = new Voucher();
        booking.setAppliedVoucher(voucher);
        booking.setDiscountAmount(new BigDecimal("10000.00"));
        booking.setSeatSubtotal(new BigDecimal("180000.00"));
        booking.setComboSubtotal(new BigDecimal("50000.00"));
        stubAuthenticatedBooking(booking);

        service.cancel(booking.getId(), "customer");

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(NOW, booking.getCancelledAt());
        assertNull(booking.getAppliedVoucher());
        assertEquals(new BigDecimal("0.00"), booking.getDiscountAmount());
        assertEquals(new BigDecimal("230000.00"), booking.getTotalAmount());
        verify(seatReservationService).releaseHeldSeats(booking);
        verify(paymentService).expirePendingAttempts(booking);
        verify(ticketService).cancelHeldTickets(booking);
    }

    @Test
    void cancelsPendingPaymentAndExpiresOrderPaymentState() {
        Booking booking = bookingAt(BookingStatus.PENDING_PAYMENT);
        booking.setPaymentExpiresAt(NOW.plusMinutes(5));
        stubAuthenticatedBooking(booking);

        service.cancel(booking.getId(), "customer");

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("EXPIRED", booking.getOrder().getPaymentStatus());
        verify(paymentService).expirePendingAttempts(booking);
    }

    @Test
    void cancellationFromConfirmedIsRejectedBeforeAnyRelease() {
        Booking booking = bookingAt(BookingStatus.CONFIRMED);
        stubAuthenticatedBooking(booking);

        assertThrows(
                InvalidBookingStateTransitionException.class,
                () -> service.cancel(booking.getId(), "customer")
        );
        verify(seatReservationService, never()).releaseHeldSeats(any());
        verify(paymentService, never()).expirePendingAttempts(any());
        verify(ticketService, never()).cancelHeldTickets(any());
    }

    @Test
    void cancellationIsIdempotentAfterBookingAlreadyCancelled() {
        Booking booking = bookingAt(BookingStatus.CANCELLED);
        stubAuthenticatedBooking(booking);

        service.cancel(booking.getId(), "customer");

        verify(seatReservationService, never()).releaseHeldSeats(any());
        verify(paymentService, never()).expirePendingAttempts(any());
        verify(ticketService, never()).cancelHeldTickets(any());
    }

    private void stubAuthenticatedBooking(Booking booking) {
        when(customerRepository.findByAccountUsernameIgnoreCase("customer"))
                .thenReturn(Optional.of(customer));
        lenient().when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
    }

    private Booking bookingAt(BookingStatus target) {
        Booking booking = new Booking();
        booking.setId(8001L);
        booking.setBookingCode("BK-TEST");
        booking.setOrder(order());
        booking.setShowtime(activeShowtime());
        booking.setCreatedAt(NOW.minusMinutes(1));
        booking.setUpdatedAt(NOW.minusMinutes(1));
        booking.setHeldAt(NOW.minusMinutes(1));
        booking.setHoldExpiresAt(NOW.plusMinutes(10));
        booking.setSeatSubtotal(new BigDecimal("90000.00"));
        booking.setComboSubtotal(BigDecimal.ZERO.setScale(2));
        booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        booking.setTotalAmount(new BigDecimal("90000.00"));
        if (target == BookingStatus.CREATED) {
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.SEAT_HELD);
        if (target == BookingStatus.SEAT_HELD) {
            return booking;
        }
        if (target == BookingStatus.CANCELLED || target == BookingStatus.EXPIRED) {
            stateMachine.transition(booking, target);
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        if (target == BookingStatus.PENDING_PAYMENT) {
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.CONFIRMED);
        if (target == BookingStatus.CONFIRMED) {
            return booking;
        }
        stateMachine.transition(booking, BookingStatus.COMPLETED);
        return booking;
    }

    private ShowtimeSeat heldSeatOwnedBy(Booking booking) {
        ShowtimeSeat seat = new ShowtimeSeat();
        seat.setShowtimeSeatId(7001L);
        seat.setBooking(booking);
        seat.setShowtime(booking.getShowtime());
        seat.setSeatStatus(ShowtimeSeatStatus.HELD);
        seat.setLockedUntil(NOW.plusMinutes(10));
        return seat;
    }

    private CinemaOrder order() {
        CinemaOrder order = new CinemaOrder();
        order.setId(7001L);
        order.setCustomer(customer);
        order.setStatus("SEAT_HELD");
        order.setPaymentStatus("UNPAID");
        order.setTotalAmount(new BigDecimal("90000.00"));
        return order;
    }

    private Customer activeCustomer() {
        Account account = new Account();
        account.setUsername("customer");
        account.setStatus("ACTIVE");
        Customer result = new Customer();
        result.setCustomerId(1001L);
        result.setAccount(account);
        return result;
    }

    private Showtime activeShowtime() {
        Branch branch = new Branch();
        branch.setStatus("ACTIVE");
        Room room = new Room();
        room.setStatus("ACTIVE");
        room.setBranch(branch);
        Movie movie = new Movie();
        movie.setStatus("ACTIVE");
        Showtime showtime = new Showtime();
        showtime.setShowtimeId(3001L);
        showtime.setStatus("AVAILABLE");
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(NOW.plusHours(2));
        showtime.setEndTime(NOW.plusHours(4));
        return showtime;
    }

    private PaymentResponse paymentResponse(Long bookingId) {
        return new PaymentResponse(
                bookingId,
                10001L,
                "PAY-TEST",
                new BigDecimal("90000.00"),
                "SIMULATED",
                "PENDING",
                "PENDING_PAYMENT",
                null,
                NOW.plusMinutes(10),
                true
        );
    }
}
