package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.CheckoutRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.dto.response.BookingSummaryResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.OrderCombo;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.exception.BookingNotFoundException;
import com.fpt.cinema.exception.BookingOwnershipException;
import com.fpt.cinema.exception.PaymentExpiredException;
import com.fpt.cinema.exception.SeatHoldExpiredException;
import com.fpt.cinema.exception.SeatUnavailableException;
import com.fpt.cinema.mapper.BookingMapper;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.OrderComboRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.service.BookingPricingService;
import com.fpt.cinema.service.BookingService;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class BookingServiceImpl implements BookingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String DEFAULT_PAYMENT_METHOD = "SIMULATED";

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final TicketRepository ticketRepository;
    private final OrderComboRepository orderComboRepository;
    private final BookingStateMachine bookingStateMachine;
    private final BookingPricingService bookingPricingService;
    private final SeatReservationService seatReservationService;
    private final PaymentService paymentService;
    private final TicketService ticketService;
    private final BookingMapper bookingMapper;
    private final Clock clock;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            ShowtimeSeatRepository showtimeSeatRepository,
            TicketRepository ticketRepository,
            OrderComboRepository orderComboRepository,
            BookingStateMachine bookingStateMachine,
            BookingPricingService bookingPricingService,
            SeatReservationService seatReservationService,
            PaymentService paymentService,
            TicketService ticketService,
            BookingMapper bookingMapper,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeSeatRepository = showtimeSeatRepository;
        this.ticketRepository = ticketRepository;
        this.orderComboRepository = orderComboRepository;
        this.bookingStateMachine = bookingStateMachine;
        this.bookingPricingService = bookingPricingService;
        this.seatReservationService = seatReservationService;
        this.paymentService = paymentService;
        this.ticketService = ticketService;
        this.bookingMapper = bookingMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSummaryResponse getSummary(Long bookingId, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        verifyOwnership(booking, customer);

        List<Ticket> tickets = ticketRepository.findAllByBookingIdOrderByIdAsc(bookingId);
        List<OrderCombo> orderCombos = orderComboRepository
                .findAllByOrderIdOrderByComboIdAsc(booking.getOrder().getId());
        return bookingMapper.toSummaryResponse(
                booking,
                tickets,
                orderCombos,
                allowedActions(booking, now)
        );
    }

    @Override
    @Transactional
    public PaymentResponse checkout(Long bookingId, CheckoutRequest request, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        verifyOwnership(booking, customer);
        String paymentMethod = normalizePaymentMethod(request == null ? null : request.paymentMethod());

        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            if (booking.getPaymentExpiresAt() == null || !now.isBefore(booking.getPaymentExpiresAt())) {
                throw new PaymentExpiredException(null);
            }
            return paymentService.createOrReusePayment(booking, paymentMethod);
        }

        bookingStateMachine.validateTransition(
                booking.getId(),
                booking.getStatus(),
                BookingStatus.PENDING_PAYMENT
        );
        requireLiveHold(booking, now);
        lockAndVerifyHeldSeats(booking, now);
        requireReservableShowtime(booking, now);

        bookingPricingService.recalculateForCheckout(booking, now);
        bookingStateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        booking.setUpdatedAt(now);
        booking.getOrder().setStatus(BookingStatus.PENDING_PAYMENT.name());
        booking.getOrder().setPaymentStatus("PENDING");

        return paymentService.createOrReusePayment(booking, paymentMethod);
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long bookingId, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking candidate = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        verifyOwnership(candidate, customer);

        if (candidate.getStatus() == BookingStatus.CANCELLED) {
            return currentBookingResponse(candidate, now);
        }
        bookingStateMachine.validateTransition(
                candidate.getId(),
                candidate.getStatus(),
                BookingStatus.CANCELLED
        );

        // Global mutation lock order: all payments (stable ID order), then booking,
        // then seats and tickets. This matches callbacks and expiration scans.
        paymentService.expirePendingAttempts(candidate);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        verifyOwnership(booking, customer);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return currentBookingResponse(booking, now);
        }

        bookingStateMachine.validateTransition(
                booking.getId(),
                booking.getStatus(),
                BookingStatus.CANCELLED
        );

        seatReservationService.releaseHeldSeats(booking);
        ticketService.cancelHeldTickets(booking);

        booking.setAppliedVoucher(null);
        booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        booking.setTotalAmount(grossAmount(booking));
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);
        booking.getOrder().setTotalAmount(booking.getTotalAmount());
        booking.getOrder().setStatus(BookingStatus.CANCELLED.name());
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            booking.getOrder().setPaymentStatus("EXPIRED");
        }
        bookingStateMachine.transition(booking, BookingStatus.CANCELLED);

        return currentBookingResponse(booking, now);
    }

    private Customer requireCustomer(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        Customer customer = customerRepository.findByAccountUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The authenticated account is not a customer"
                ));
        if (!ACTIVE.equalsIgnoreCase(customer.getAccount().getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is inactive");
        }
        return customer;
    }

    private void verifyOwnership(Booking booking, Customer customer) {
        Customer owner = booking.getOrder().getCustomer();
        if (owner == null || !Objects.equals(owner.getCustomerId(), customer.getCustomerId())) {
            throw new BookingOwnershipException(booking.getId());
        }
    }

    private void requireLiveHold(Booking booking, LocalDateTime now) {
        if (booking.getHoldExpiresAt() == null || !now.isBefore(booking.getHoldExpiresAt())) {
            throw new SeatHoldExpiredException(booking.getId());
        }
    }

    private void lockAndVerifyHeldSeats(Booking booking, LocalDateTime now) {
        List<ShowtimeSeat> seats = showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId());
        if (seats.isEmpty()) {
            throw new SeatUnavailableException(List.of());
        }

        List<Long> invalidSeats = seats.stream()
                .filter(seat -> seat.getSeatStatus() != ShowtimeSeatStatus.HELD
                        || seat.getBooking() == null
                        || !Objects.equals(seat.getBooking().getId(), booking.getId())
                        || !Objects.equals(
                                seat.getShowtime().getShowtimeId(),
                                booking.getShowtime().getShowtimeId()
                        )
                        || seat.getLockedUntil() == null
                        || !now.isBefore(seat.getLockedUntil()))
                .map(ShowtimeSeat::getShowtimeSeatId)
                .toList();
        if (!invalidSeats.isEmpty()) {
            throw new SeatUnavailableException(invalidSeats);
        }

    }

    private void requireReservableShowtime(Booking booking, LocalDateTime now) {
        var showtime = booking.getShowtime();
        boolean activePath = AVAILABLE.equalsIgnoreCase(showtime.getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getMovie().getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getRoom().getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getRoom().getBranch().getStatus());
        if (!activePath || !showtime.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This showtime is no longer available for checkout"
            );
        }
    }

    private BookingResponse currentBookingResponse(Booking booking, LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findAllByBookingIdOrderByIdAsc(booking.getId());
        List<OrderCombo> orderCombos = orderComboRepository
                .findAllByOrderIdOrderByComboIdAsc(booking.getOrder().getId());
        return bookingMapper.toBookingResponse(booking, tickets, orderCombos, now);
    }

    private List<String> allowedActions(Booking booking, LocalDateTime now) {
        List<String> actions = new ArrayList<>();
        if (booking.getStatus() == BookingStatus.SEAT_HELD
                && booking.getHoldExpiresAt() != null
                && now.isBefore(booking.getHoldExpiresAt())) {
            actions.add("UPDATE_COMBOS");
            actions.add("APPLY_VOUCHER");
            if (booking.getAppliedVoucher() != null) {
                actions.add("REMOVE_VOUCHER");
            }
            actions.add("CHECKOUT");
            actions.add("CANCEL");
        } else if (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                && booking.getPaymentExpiresAt() != null
                && now.isBefore(booking.getPaymentExpiresAt())) {
            actions.add("RETRY_PAYMENT");
            actions.add("CANCEL");
        } else if (booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            actions.add("VIEW_TICKETS");
        }
        return List.copyOf(actions);
    }

    private String normalizePaymentMethod(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank()
                ? DEFAULT_PAYMENT_METHOD
                : paymentMethod.trim().toUpperCase();
    }

    private BigDecimal grossAmount(Booking booking) {
        BigDecimal seatSubtotal = booking.getSeatSubtotal() == null ? BigDecimal.ZERO : booking.getSeatSubtotal();
        BigDecimal comboSubtotal = booking.getComboSubtotal() == null ? BigDecimal.ZERO : booking.getComboSubtotal();
        return seatSubtotal.add(comboSubtotal).setScale(2);
    }
}
