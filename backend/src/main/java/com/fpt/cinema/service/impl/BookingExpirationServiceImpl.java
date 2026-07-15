package com.fpt.cinema.service.impl;

import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.service.BookingExpirationService;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import com.fpt.cinema.service.TicketService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingExpirationServiceImpl implements BookingExpirationService {

    private static final int MAX_BATCH_SIZE = 500;

    private final BookingRepository bookingRepository;
    private final SeatReservationService seatReservationService;
    private final PaymentService paymentService;
    private final TicketService ticketService;
    private final BookingStateMachine bookingStateMachine;
    private final EntityManager entityManager;
    private final Clock clock;
    private final int batchSize;

    public BookingExpirationServiceImpl(
            BookingRepository bookingRepository,
            SeatReservationService seatReservationService,
            PaymentService paymentService,
            TicketService ticketService,
            BookingStateMachine bookingStateMachine,
            EntityManager entityManager,
            Clock clock,
            @Value("${booking.expiration-batch-size:100}") int configuredBatchSize
    ) {
        this.bookingRepository = bookingRepository;
        this.seatReservationService = seatReservationService;
        this.paymentService = paymentService;
        this.ticketService = ticketService;
        this.bookingStateMachine = bookingStateMachine;
        this.entityManager = entityManager;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(configuredBatchSize, MAX_BATCH_SIZE));
    }

    @Override
    @Transactional
    public int expireSeatHeldBookings() {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Booking> candidates = bookingRepository
                .findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc(
                        BookingStatus.SEAT_HELD,
                        now,
                        PageRequest.of(0, batchSize)
                );
        return expireCandidates(candidates.getContent(), BookingStatus.SEAT_HELD, now, false);
    }

    @Override
    @Transactional
    public int expirePendingPaymentBookings() {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Booking> candidates = bookingRepository
                .findByStatusAndPaymentExpiresAtLessThanEqualOrderByIdAsc(
                        BookingStatus.PENDING_PAYMENT,
                        now,
                        PageRequest.of(0, batchSize)
                );
        return expireCandidates(candidates.getContent(), BookingStatus.PENDING_PAYMENT, now, true);
    }

    @Override
    @Transactional
    public int completeFinishedBookings() {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Booking> candidates = bookingRepository.findCompletionCandidates(
                BookingStatus.CONFIRMED,
                now,
                PageRequest.of(0, batchSize)
        );

        int completed = 0;
        for (Booking candidate : candidates.getContent()) {
            Booking booking = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            refreshAfterLock(booking);
            if (booking == null
                    || booking.getStatus() != BookingStatus.CONFIRMED
                    || booking.getShowtime().getEndTime().isAfter(now)) {
                continue;
            }
            bookingStateMachine.transition(booking, BookingStatus.COMPLETED);
            booking.setCompletedAt(now);
            booking.setUpdatedAt(now);
            booking.getOrder().setStatus(BookingStatus.COMPLETED.name());
            completed++;
        }
        return completed;
    }

    private int expireCandidates(
            List<Booking> candidates,
            BookingStatus expectedStatus,
            LocalDateTime now,
            boolean expirePayments
    ) {
        int expired = 0;
        for (Booking candidate : candidates) {
            // Pending-payment scans follow the gateway lock order: payments, then booking.
            // If another transaction confirms first, SUCCESS is preserved and the status
            // recheck below prevents any seat release.
            if (expirePayments) {
                paymentService.expirePendingAttempts(candidate);
            }
            Booking booking = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            refreshAfterLock(booking);
            if (booking == null || booking.getStatus() != expectedStatus || deadlineIsFuture(booking, now)) {
                continue;
            }

            seatReservationService.releaseHeldSeats(booking);
            ticketService.expireHeldTickets(booking);
            booking.setAppliedVoucher(null);
            booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
            booking.setTotalAmount(grossAmount(booking));
            bookingStateMachine.transition(booking, BookingStatus.EXPIRED);
            booking.setExpiredAt(now);
            booking.setUpdatedAt(now);
            booking.getOrder().setTotalAmount(booking.getTotalAmount());
            if (expectedStatus == BookingStatus.PENDING_PAYMENT) {
                booking.getOrder().setPaymentStatus("EXPIRED");
            }
            booking.getOrder().setStatus(BookingStatus.EXPIRED.name());
            expired++;
        }
        return expired;
    }

    private boolean deadlineIsFuture(Booking booking, LocalDateTime now) {
        LocalDateTime deadline = booking.getStatus() == BookingStatus.SEAT_HELD
                ? booking.getHoldExpiresAt()
                : booking.getPaymentExpiresAt();
        return deadline == null || deadline.isAfter(now);
    }

    private void refreshAfterLock(Booking booking) {
        if (booking != null) {
            // Candidate rows were selected before the lock. Refresh after any wait so
            // state/deadline checks use the latest committed database values.
            entityManager.refresh(booking);
        }
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
}
