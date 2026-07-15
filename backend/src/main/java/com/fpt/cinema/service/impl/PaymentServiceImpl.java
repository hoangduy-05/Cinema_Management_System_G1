package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.PaymentCallbackRequest;
import com.fpt.cinema.dto.response.PaymentCallbackResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Payment;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Voucher;
import com.fpt.cinema.entity.VoucherUsage;
import com.fpt.cinema.entity.VoucherUsageId;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.enums.PaymentStatus;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.event.BookingConfirmedEvent;
import com.fpt.cinema.exception.BookingNotFoundException;
import com.fpt.cinema.exception.BookingOwnershipException;
import com.fpt.cinema.exception.DuplicatePaymentException;
import com.fpt.cinema.exception.PaymentExpiredException;
import com.fpt.cinema.exception.PaymentVerificationException;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.PaymentRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.repository.VoucherRepository;
import com.fpt.cinema.repository.VoucherUsageRepository;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String DEFAULT_PAYMENT_METHOD = "SIMULATED";
    private static final String SIMULATED_CALLBACK_URL = "/api/v1/payments/callback";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final SeatReservationService seatReservationService;
    private final TicketService ticketService;
    private final BookingStateMachine bookingStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final long paymentTimeoutMinutes;
    private final String simulationToken;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            ShowtimeSeatRepository showtimeSeatRepository,
            VoucherRepository voucherRepository,
            VoucherUsageRepository voucherUsageRepository,
            SeatReservationService seatReservationService,
            TicketService ticketService,
            BookingStateMachine bookingStateMachine,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${booking.payment-timeout-minutes:10}") long paymentTimeoutMinutes,
            @Value("${booking.payment.simulation-token}") String simulationToken
    ) {
        if (paymentTimeoutMinutes <= 0) {
            throw new IllegalArgumentException("booking.payment-timeout-minutes must be positive");
        }
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.showtimeSeatRepository = showtimeSeatRepository;
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.seatReservationService = seatReservationService;
        this.ticketService = ticketService;
        this.bookingStateMachine = bookingStateMachine;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
        this.simulationToken = simulationToken == null ? "" : simulationToken;
    }

    @Override
    @Transactional
    public PaymentResponse createOrReusePayment(Booking booking, String paymentMethod) {
        Objects.requireNonNull(booking, "booking must not be null");
        if (booking.getId() == null) {
            throw new IllegalArgumentException("Booking must be persisted before payment is created");
        }
        Booking lockedBooking = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new BookingNotFoundException(booking.getId()));
        return createOrReuseLocked(lockedBooking, paymentMethod, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(Long bookingId, String username, String paymentMethod) {
        Customer customer = requireCustomer(username);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        verifyOwnership(booking, customer.getCustomerId());
        return createOrReuseLocked(booking, paymentMethod, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public PaymentResponse retryPayment(Long bookingId, String username, String paymentMethod) {
        Customer customer = requireCustomer(username);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return retryLocked(booking, customer.getCustomerId(), paymentMethod, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public PaymentResponse retryPaymentAttempt(Long paymentId, String username, String paymentMethod) {
        Customer customer = requireCustomer(username);
        Payment candidate = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentVerificationException("Payment attempt was not found."));
        Payment payment = lockAllAttemptsAndFindById(candidate.getOrder().getId(), paymentId);
        Booking booking = bookingRepository.findByOrderIdForUpdate(payment.getOrder().getId())
                .orElseThrow(() -> new PaymentVerificationException("Payment is not linked to a booking."));
        return retryLocked(booking, customer.getCustomerId(), paymentMethod, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public PaymentCallbackResponse processCallback(PaymentCallbackRequest request) {
        verifySimulationToken(request == null ? null : request.simulationToken());
        if (request == null || request.transactionReference() == null) {
            throw new PaymentVerificationException();
        }

        CallbackStatus callbackStatus = parseCallbackStatus(request.status());
        Payment candidate = paymentRepository.findByTransactionCode(request.transactionReference())
                .orElseThrow(() -> new PaymentVerificationException("Payment transaction was not found."));
        Payment payment = lockAllAttemptsAndFindByReference(
                candidate.getOrder().getId(),
                request.transactionReference()
        );
        Booking booking = bookingRepository.findByOrderIdForUpdate(payment.getOrder().getId())
                .orElseThrow(() -> new PaymentVerificationException("Payment is not linked to a booking."));

        verifyReference(payment, request.transactionReference());
        verifyAmount(payment, booking, request.amount());

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return callbackResponse(
                    payment,
                    booking,
                    true,
                    false,
                    "Payment was already processed successfully."
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            if (booking.getStatus() == BookingStatus.EXPIRED) {
                return callbackResponse(payment, booking, false, false, "The booking has expired.");
            }
            throw new PaymentVerificationException("The booking is not awaiting payment.");
        }

        if (callbackStatus == CallbackStatus.FAILED) {
            return processFailedCallback(payment, booking, now);
        }
        return processSuccessfulCallback(payment, booking, now);
    }

    @Override
    @Transactional
    public void expirePendingAttempts(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        for (Payment payment : paymentRepository.findAllByOrderIdForUpdate(booking.getOrder().getId())) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.EXPIRED);
            }
        }
    }

    private PaymentResponse retryLocked(
            Booking booking,
            Long customerId,
            String paymentMethod,
            LocalDateTime now
    ) {
        verifyOwnership(booking, customerId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The booking is not eligible for payment retry.");
        }
        requireActivePaymentDeadline(booking, now);
        if (paymentRepository.existsByOrderIdAndStatus(booking.getOrder().getId(), PaymentStatus.SUCCESS)) {
            throw new DuplicatePaymentException(booking.getId());
        }

        Payment existing = findActivePendingPayment(booking, now);
        if (existing != null) {
            return toPaymentResponse(booking, existing, true);
        }

        bookingStateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        booking.setUpdatedAt(now);
        return createNewPayment(booking, normalizePaymentMethod(paymentMethod));
    }

    private PaymentResponse createOrReuseLocked(
            Booking booking,
            String paymentMethod,
            LocalDateTime now
    ) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The booking is not awaiting payment.");
        }
        if (booking.getPaymentExpiresAt() == null) {
            booking.setPaymentExpiresAt(now.plusMinutes(paymentTimeoutMinutes));
        }
        requireActivePaymentDeadline(booking, now);
        extendHeldSeatDeadline(booking);
        booking.setUpdatedAt(now);

        if (paymentRepository.existsByOrderIdAndStatus(booking.getOrder().getId(), PaymentStatus.SUCCESS)) {
            throw new DuplicatePaymentException(booking.getId());
        }
        Payment existing = findActivePendingPayment(booking, now);
        if (existing != null) {
            return toPaymentResponse(booking, existing, true);
        }
        return createNewPayment(booking, normalizePaymentMethod(paymentMethod));
    }

    private PaymentResponse createNewPayment(Booking booking, String paymentMethod) {
        Payment payment = new Payment();
        payment.setOrder(booking.getOrder());
        payment.setMethod(paymentMethod);
        payment.setAmount(booking.getTotalAmount());
        payment.setTransactionCode(newTransactionReference());
        payment.setExpiresAt(booking.getPaymentExpiresAt());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);
        return toPaymentResponse(booking, payment, true);
    }

    private Payment processPaymentSuccess(Payment payment, Booking booking, LocalDateTime now) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentVerificationException("This payment attempt can no longer be completed.");
        }
        if (paymentRepository.existsByOrderIdAndStatus(booking.getOrder().getId(), PaymentStatus.SUCCESS)) {
            throw new DuplicatePaymentException(booking.getId());
        }

        List<ShowtimeSeat> seats = showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId());
        verifyHeldSeatOwnership(booking, seats);

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(now);
        bookingStateMachine.transition(booking, BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);
        booking.setUpdatedAt(now);
        booking.getOrder().setPaymentStatus("PAID");
        booking.getOrder().setStatus(BookingStatus.CONFIRMED.name());

        for (ShowtimeSeat seat : seats) {
            seat.setSeatStatus(ShowtimeSeatStatus.BOOKED);
            seat.setLockedUntil(null);
        }
        showtimeSeatRepository.saveAll(seats);
        ticketService.issueOrActivateTickets(booking, seats);
        finalizeVoucherUsage(booking, now);
        eventPublisher.publishEvent(new BookingConfirmedEvent(booking.getId()));
        return payment;
    }

    private PaymentCallbackResponse processSuccessfulCallback(
            Payment payment,
            Booking booking,
            LocalDateTime now
    ) {
        if (paymentDeadlineExpired(booking, now) || paymentAttemptExpired(payment, now)) {
            expireBookingAfterPaymentDeadline(booking, now);
            return callbackResponse(payment, booking, false, false, "The payment deadline has expired.");
        }
        processPaymentSuccess(payment, booking, now);
        return callbackResponse(payment, booking, true, false, "Payment completed successfully.");
    }

    private PaymentCallbackResponse processFailedCallback(
            Payment payment,
            Booking booking,
            LocalDateTime now
    ) {
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
        }
        if (paymentDeadlineExpired(booking, now)) {
            payment.setStatus(PaymentStatus.EXPIRED);
            expireBookingAfterPaymentDeadline(booking, now);
            return callbackResponse(payment, booking, false, false, "Payment failed and the booking expired.");
        }

        // This validated self-transition records that retry remains a legal domain action.
        bookingStateMachine.transition(booking, BookingStatus.PENDING_PAYMENT);
        booking.setUpdatedAt(now);
        return callbackResponse(payment, booking, false, true, "Payment failed. A retry is allowed before the deadline.");
    }

    private void expireBookingAfterPaymentDeadline(Booking booking, LocalDateTime now) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return;
        }
        seatReservationService.releaseHeldSeats(booking);
        expirePendingAttempts(booking);
        ticketService.expireHeldTickets(booking);
        booking.setAppliedVoucher(null);
        booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        booking.setTotalAmount(grossAmount(booking));
        bookingStateMachine.transition(booking, BookingStatus.EXPIRED);
        booking.setExpiredAt(now);
        booking.setUpdatedAt(now);
        booking.getOrder().setTotalAmount(booking.getTotalAmount());
        booking.getOrder().setPaymentStatus("EXPIRED");
        booking.getOrder().setStatus(BookingStatus.EXPIRED.name());
    }

    private void finalizeVoucherUsage(Booking booking, LocalDateTime now) {
        Voucher appliedVoucher = booking.getAppliedVoucher();
        if (appliedVoucher == null) {
            return;
        }
        Long voucherId = appliedVoucher.getId();
        Long orderId = booking.getOrder().getId();
        if (voucherUsageRepository.existsForVoucherAndOrder(voucherId, orderId)) {
            return;
        }

        Voucher lockedVoucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new PaymentVerificationException("The applied voucher no longer exists."));
        Long customerId = booking.getOrder().getCustomer() == null
                ? null
                : booking.getOrder().getCustomer().getCustomerId();
        if (customerId == null) {
            throw new PaymentVerificationException("The voucher customer could not be verified.");
        }
        if (voucherUsageRepository.countByVoucherAndCustomer(voucherId, customerId) > 0) {
            throw new PaymentVerificationException("This voucher has already been used by the customer.");
        }
        int usedCount = lockedVoucher.getUsedCount() == null ? 0 : lockedVoucher.getUsedCount();
        if (lockedVoucher.getUsageLimit() == null || usedCount >= lockedVoucher.getUsageLimit()) {
            throw new PaymentVerificationException("The applied voucher usage limit has been reached.");
        }

        VoucherUsageId usageId = new VoucherUsageId();
        usageId.setVoucherId(voucherId);
        usageId.setOrderId(orderId);

        VoucherUsage usage = new VoucherUsage();
        usage.setId(usageId);
        usage.setVoucher(lockedVoucher);
        usage.setOrder(booking.getOrder());
        usage.setUsedAt(now);
        usage.setDiscountAmount(booking.getDiscountAmount());
        voucherUsageRepository.save(usage);
        lockedVoucher.setUsedCount(usedCount + 1);
    }

    private void verifyHeldSeatOwnership(Booking booking, List<ShowtimeSeat> seats) {
        if (seats.isEmpty()) {
            throw new PaymentVerificationException("The booking has no held seats.");
        }
        for (ShowtimeSeat seat : seats) {
            boolean owned = seat.getBooking() != null
                    && Objects.equals(seat.getBooking().getId(), booking.getId());
            boolean sameShowtime = Objects.equals(
                    seat.getShowtime().getShowtimeId(),
                    booking.getShowtime().getShowtimeId()
            );
            if (!owned || !sameShowtime || seat.getSeatStatus() != ShowtimeSeatStatus.HELD) {
                throw new PaymentVerificationException("One or more held seats can no longer be confirmed.");
            }
        }
    }

    private void verifyOwnership(Booking booking, Long customerId) {
        Long ownerId = booking.getOrder().getCustomer() == null
                ? null
                : booking.getOrder().getCustomer().getCustomerId();
        if (!Objects.equals(ownerId, customerId)) {
            throw new BookingOwnershipException(booking.getId());
        }
    }

    private Customer requireCustomer(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        Customer customer = customerRepository.findByAccountUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The authenticated account is not a customer."
                ));
        if (!"ACTIVE".equalsIgnoreCase(customer.getAccount().getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is inactive.");
        }
        return customer;
    }

    private void extendHeldSeatDeadline(Booking booking) {
        List<ShowtimeSeat> seats = showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId());
        if (seats.isEmpty()) {
            throw new PaymentVerificationException("The booking has no held seats.");
        }
        for (ShowtimeSeat seat : seats) {
            boolean owned = seat.getBooking() != null
                    && Objects.equals(seat.getBooking().getId(), booking.getId());
            if (!owned || seat.getSeatStatus() != ShowtimeSeatStatus.HELD) {
                throw new PaymentVerificationException("One or more seats are no longer held by this booking.");
            }
            if (seat.getLockedUntil() == null
                    || seat.getLockedUntil().isBefore(booking.getPaymentExpiresAt())) {
                seat.setLockedUntil(booking.getPaymentExpiresAt());
            }
        }
    }

    private Payment findActivePendingPayment(Booking booking, LocalDateTime now) {
        return paymentRepository.findFirstByOrderIdAndStatusAndExpiresAtAfterOrderByIdDesc(
                        booking.getOrder().getId(),
                        PaymentStatus.PENDING,
                        now
                )
                .orElse(null);
    }

    private Payment lockAllAttemptsAndFindById(Long orderId, Long paymentId) {
        return paymentRepository.findAllByOrderIdForUpdate(orderId).stream()
                .filter(payment -> Objects.equals(payment.getId(), paymentId))
                .findFirst()
                .orElseThrow(() -> new PaymentVerificationException("Payment attempt was not found."));
    }

    private Payment lockAllAttemptsAndFindByReference(Long orderId, String transactionReference) {
        return paymentRepository.findAllByOrderIdForUpdate(orderId).stream()
                .filter(payment -> Objects.equals(payment.getTransactionCode(), transactionReference))
                .findFirst()
                .orElseThrow(() -> new PaymentVerificationException("Payment transaction was not found."));
    }

    private void requireActivePaymentDeadline(Booking booking, LocalDateTime now) {
        if (paymentDeadlineExpired(booking, now)) {
            throw new PaymentExpiredException(null);
        }
    }

    private boolean paymentDeadlineExpired(Booking booking, LocalDateTime now) {
        return booking.getPaymentExpiresAt() == null || !booking.getPaymentExpiresAt().isAfter(now);
    }

    private boolean paymentAttemptExpired(Payment payment, LocalDateTime now) {
        return payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(now);
    }

    private void verifySimulationToken(String suppliedToken) {
        if (simulationToken.isBlank()) {
            throw new PaymentVerificationException(
                    "Simulated payment callback is disabled until BOOKING_PAYMENT_SIMULATION_TOKEN is configured."
            );
        }
        byte[] expected = simulationToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null
                ? new byte[0]
                : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new PaymentVerificationException();
        }
    }

    private void verifyReference(Payment payment, String transactionReference) {
        if (!Objects.equals(payment.getTransactionCode(), transactionReference)) {
            throw new PaymentVerificationException();
        }
    }

    private void verifyAmount(Payment payment, Booking booking, BigDecimal callbackAmount) {
        if (callbackAmount == null
                || payment.getAmount() == null
                || booking.getTotalAmount() == null
                || callbackAmount.compareTo(payment.getAmount()) != 0
                || callbackAmount.compareTo(booking.getTotalAmount()) != 0) {
            throw new PaymentVerificationException("Payment amount verification failed.");
        }
    }

    private CallbackStatus parseCallbackStatus(String rawStatus) {
        if (rawStatus == null) {
            throw new PaymentVerificationException("Payment callback status is invalid.");
        }
        try {
            return CallbackStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PaymentVerificationException("Payment callback status is invalid.");
        }
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = paymentMethod == null || paymentMethod.isBlank()
                ? DEFAULT_PAYMENT_METHOD
                : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Payment method cannot exceed 50 characters");
        }
        return normalized;
    }

    private String newTransactionReference() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private PaymentResponse toPaymentResponse(Booking booking, Payment payment, boolean retryAllowed) {
        return new PaymentResponse(
                booking.getId(),
                payment.getId(),
                payment.getTransactionCode(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus().name(),
                booking.getStatus().name(),
                SIMULATED_CALLBACK_URL,
                booking.getPaymentExpiresAt(),
                retryAllowed
        );
    }

    private BigDecimal grossAmount(Booking booking) {
        BigDecimal seatSubtotal = booking.getSeatSubtotal() == null
                ? BigDecimal.ZERO
                : booking.getSeatSubtotal();
        BigDecimal comboSubtotal = booking.getComboSubtotal() == null
                ? BigDecimal.ZERO
                : booking.getComboSubtotal();
        return seatSubtotal.add(comboSubtotal).setScale(2);
    }

    private PaymentCallbackResponse callbackResponse(
            Payment payment,
            Booking booking,
            boolean successful,
            boolean retryAllowed,
            String message
    ) {
        return new PaymentCallbackResponse(
                booking.getId(),
                payment.getId(),
                payment.getTransactionCode(),
                payment.getStatus().name(),
                booking.getStatus().name(),
                successful,
                retryAllowed,
                message
        );
    }

    private enum CallbackStatus {
        SUCCESS,
        FAILED
    }
}
