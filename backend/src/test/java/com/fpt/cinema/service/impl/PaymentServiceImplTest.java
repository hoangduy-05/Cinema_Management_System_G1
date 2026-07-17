package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.PaymentCallbackRequest;
import com.fpt.cinema.dto.response.PaymentCallbackResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.CinemaOrder;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Payment;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Voucher;
import com.fpt.cinema.entity.VoucherUsage;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.enums.PaymentStatus;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.event.BookingConfirmedEvent;
import com.fpt.cinema.exception.PaymentExpiredException;
import com.fpt.cinema.exception.PaymentVerificationException;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.PaymentRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.repository.VoucherRepository;
import com.fpt.cinema.repository.VoucherUsageRepository;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T03:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 14, 10, 0);
    private static final String TOKEN = "test-simulation-token";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;
    @Mock
    private SeatReservationService seatReservationService;
    @Mock
    private TicketService ticketService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingStateMachine stateMachine;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        stateMachine = new BookingStateMachine();
        service = new PaymentServiceImpl(
                paymentRepository,
                bookingRepository,
                customerRepository,
                showtimeSeatRepository,
                voucherRepository,
                voucherUsageRepository,
                seatReservationService,
                ticketService,
                stateMachine,
                eventPublisher,
                CLOCK,
                10,
                TOKEN,
                true
        );
    }

    @Test
    void createsOnePendingAttemptAndExtendsSeatLockToPaymentDeadline() {
        Booking booking = pendingBooking();
        booking.setPaymentExpiresAt(null);
        ShowtimeSeat seat = heldSeat(booking);
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(seat));

        PaymentResponse response = service.createOrReusePayment(booking, "vnpay");

        assertEquals(NOW.plusMinutes(10), booking.getPaymentExpiresAt());
        assertEquals(booking.getPaymentExpiresAt(), seat.getLockedUntil());
        assertEquals("VNPAY", response.paymentMethod());
        assertEquals("PENDING", response.paymentStatus());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.PENDING, captor.getValue().getStatus());
        assertEquals(booking.getTotalAmount(), captor.getValue().getAmount());
        assertEquals(booking.getPaymentExpiresAt(), captor.getValue().getExpiresAt());
    }

    @Test
    void repeatedCheckoutReusesExistingLivePendingAttempt() {
        Booking booking = pendingBooking();
        ShowtimeSeat seat = heldSeat(booking);
        Payment existing = pendingPayment(booking);
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(seat));
        when(paymentRepository.findFirstByOrderIdAndStatusAndExpiresAtAfterOrderByIdDesc(
                booking.getOrder().getId(), PaymentStatus.PENDING, NOW
        )).thenReturn(Optional.of(existing));

        PaymentResponse response = service.createOrReusePayment(booking, "SIMULATED");

        assertEquals(existing.getId(), response.paymentId());
        assertEquals(existing.getTransactionCode(), response.transactionReference());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void retryAfterFailedAttemptCreatesNewPendingAttemptAndKeepsBookingPending() {
        Booking booking = pendingBooking();
        Customer customer = booking.getOrder().getCustomer();
        when(customerRepository.findByAccountUsernameIgnoreCase("customer")).thenReturn(Optional.of(customer));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        PaymentResponse response = service.retryPayment(booking.getId(), "customer", "momo");

        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertEquals("MOMO", response.paymentMethod());
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void retryIsRejectedAfterPaymentDeadline() {
        Booking booking = pendingBooking();
        booking.setPaymentExpiresAt(NOW);
        Customer customer = booking.getOrder().getCustomer();
        when(customerRepository.findByAccountUsernameIgnoreCase("customer")).thenReturn(Optional.of(customer));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(
                PaymentExpiredException.class,
                () -> service.retryPayment(booking.getId(), "customer", "SIMULATED")
        );
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void successfulCallbackConfirmsBookingBooksSeatsAndIsIdempotent() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);
        ShowtimeSeat seat = heldSeat(booking);
        stubCallback(payment, booking);
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(seat));

        PaymentCallbackRequest request = callback(payment, "SUCCESS", booking.getTotalAmount(), TOKEN);
        PaymentCallbackResponse first = service.processCallback(request);
        PaymentCallbackResponse repeated = service.processCallback(request);

        assertTrue(first.successful());
        assertTrue(repeated.successful());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(ShowtimeSeatStatus.BOOKED, seat.getSeatStatus());
        assertNull(seat.getLockedUntil());
        assertEquals(NOW, booking.getConfirmedAt());
        verify(ticketService, times(1)).issueOrActivateTickets(booking, List.of(seat));
        verify(eventPublisher, times(1)).publishEvent(any(BookingConfirmedEvent.class));
    }

    @Test
    void successfulCallbackFinalizesVoucherExactlyOnce() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);
        ShowtimeSeat seat = heldSeat(booking);
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setUsageLimit(10);
        voucher.setUsedCount(2);
        booking.setAppliedVoucher(voucher);
        booking.setDiscountAmount(new BigDecimal("20000.00"));
        stubCallback(payment, booking);
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(seat));
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));

        PaymentCallbackRequest request = callback(payment, "SUCCESS", booking.getTotalAmount(), TOKEN);
        service.processCallback(request);
        service.processCallback(request);

        ArgumentCaptor<VoucherUsage> usageCaptor = ArgumentCaptor.forClass(VoucherUsage.class);
        verify(voucherUsageRepository, times(1)).save(usageCaptor.capture());
        assertEquals(3, voucher.getUsedCount());
        assertEquals(booking.getDiscountAmount(), usageCaptor.getValue().getDiscountAmount());
    }

    @Test
    void browserConfirmationPersistsSuccessfulOwnedPayment() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);
        ShowtimeSeat seat = heldSeat(booking);
        Customer customer = booking.getOrder().getCustomer();
        when(customerRepository.findByAccountUsernameIgnoreCase("customer")).thenReturn(Optional.of(customer));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByOrderIdForUpdate(payment.getOrder().getId())).thenReturn(List.of(payment));
        when(bookingRepository.findByOrderIdForUpdate(booking.getOrder().getId())).thenReturn(Optional.of(booking));
        when(showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(seat));

        PaymentCallbackResponse response = service.confirmBrowserPayment(payment.getId(), "customer");

        assertTrue(response.successful());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("PAID", booking.getOrder().getPaymentStatus());
        assertEquals(ShowtimeSeatStatus.BOOKED, seat.getSeatStatus());
        verify(ticketService).issueOrActivateTickets(booking, List.of(seat));
        verify(eventPublisher).publishEvent(any(BookingConfirmedEvent.class));
    }

    @Test
    void failedAttemptKeepsBookingAndSeatsHeldWhileRetryWindowIsOpen() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);
        stubCallback(payment, booking);

        PaymentCallbackResponse response = service.processCallback(
                callback(payment, "FAILED", booking.getTotalAmount(), TOKEN)
        );

        assertFalse(response.successful());
        assertTrue(response.retryAllowed());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        verify(seatReservationService, never()).releaseHeldSeats(any());
        verify(ticketService, never()).expireHeldTickets(any());
    }

    @Test
    void failedAttemptAtDeadlineExpiresBookingAndReleasesHeldResources() {
        Booking booking = pendingBooking();
        booking.setPaymentExpiresAt(NOW);
        Payment payment = pendingPayment(booking);
        payment.setExpiresAt(NOW);
        stubCallback(payment, booking);

        PaymentCallbackResponse response = service.processCallback(
                callback(payment, "FAILED", booking.getTotalAmount(), TOKEN)
        );

        assertFalse(response.retryAllowed());
        assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(NOW, booking.getExpiredAt());
        verify(seatReservationService).releaseHeldSeats(booking);
        verify(ticketService).expireHeldTickets(booking);
    }

    @Test
    void rejectsAmountMismatchBeforeChangingPaymentOrBooking() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);
        stubCallback(payment, booking);

        assertThrows(
                PaymentVerificationException.class,
                () -> service.processCallback(callback(payment, "SUCCESS", BigDecimal.ONE, TOKEN))
        );
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        verify(showtimeSeatRepository, never()).findAllByBookingIdForUpdate(any());
    }

    @Test
    void rejectsInvalidSimulationTokenBeforeLookingUpPayment() {
        Booking booking = pendingBooking();
        Payment payment = pendingPayment(booking);

        assertThrows(
                PaymentVerificationException.class,
                () -> service.processCallback(callback(payment, "SUCCESS", booking.getTotalAmount(), "wrong"))
        );
        verify(paymentRepository, never()).findByTransactionCode(any());
    }

    private void stubCallback(Payment payment, Booking booking) {
        when(paymentRepository.findByTransactionCode(payment.getTransactionCode()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByOrderIdForUpdate(payment.getOrder().getId()))
                .thenReturn(List.of(payment));
        when(bookingRepository.findByOrderIdForUpdate(booking.getOrder().getId()))
                .thenReturn(Optional.of(booking));
    }

    private Booking pendingBooking() {
        Customer customer = activeCustomer();
        CinemaOrder order = new CinemaOrder();
        order.setId(7001L);
        order.setCustomer(customer);
        order.setStatus("PENDING_PAYMENT");
        order.setPaymentStatus("PENDING");
        Showtime showtime = new Showtime();
        showtime.setShowtimeId(3001L);

        Booking booking = new Booking();
        booking.setId(8001L);
        booking.setOrder(order);
        booking.setShowtime(showtime);
        booking.setTotalAmount(new BigDecimal("90000.00"));
        booking.setPaymentExpiresAt(NOW.plusMinutes(10));
        booking.setUpdatedAt(NOW.minusMinutes(1));
        stateMachine.transition(booking, BookingStatus.SEAT_HELD);
        stateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        return booking;
    }

    private Payment pendingPayment(Booking booking) {
        Payment payment = new Payment();
        payment.setId(10001L);
        payment.setOrder(booking.getOrder());
        payment.setMethod("SIMULATED");
        payment.setAmount(booking.getTotalAmount());
        payment.setTransactionCode("PAY-TEST");
        payment.setExpiresAt(booking.getPaymentExpiresAt());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private ShowtimeSeat heldSeat(Booking booking) {
        ShowtimeSeat seat = new ShowtimeSeat();
        seat.setShowtimeSeatId(7001L);
        seat.setBooking(booking);
        seat.setShowtime(booking.getShowtime());
        seat.setSeatStatus(ShowtimeSeatStatus.HELD);
        seat.setLockedUntil(NOW.plusMinutes(5));
        return seat;
    }

    private Customer activeCustomer() {
        Account account = new Account();
        account.setUsername("customer");
        account.setStatus("ACTIVE");
        Customer customer = new Customer();
        customer.setCustomerId(1001L);
        customer.setAccount(account);
        return customer;
    }

    private PaymentCallbackRequest callback(
            Payment payment,
            String status,
            BigDecimal amount,
            String token
    ) {
        return new PaymentCallbackRequest(payment.getTransactionCode(), status, amount, token);
    }
}
