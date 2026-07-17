package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingCode(String bookingCode);

    Optional<Booking> findByIdAndOrderCustomerCustomerId(Long bookingId, Long customerId);

    Page<Booking> findAllByOrderCustomerCustomerIdOrderByCreatedAtDescIdDesc(
            Long customerId,
            Pageable pageable
    );

    Page<Booking> findAllByOrderCustomerCustomerIdAndStatusOrderByCreatedAtDescIdDesc(
            Long customerId,
            BookingStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.order.id = :orderId")
    Optional<Booking> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Query("""
            select count(booking)
            from Booking booking
            where booking.appliedVoucher.id = :voucherId
              and booking.status in :statuses
              and booking.id <> :excludedBookingId
            """)
    long countActiveVoucherReservations(
            @Param("voucherId") Long voucherId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("excludedBookingId") Long excludedBookingId
    );

    @Query("""
            select count(booking)
            from Booking booking
            where booking.appliedVoucher.id = :voucherId
              and booking.order.customer.customerId = :customerId
              and booking.status in :statuses
              and booking.id <> :excludedBookingId
            """)
    long countActiveCustomerVoucherReservations(
            @Param("voucherId") Long voucherId,
            @Param("customerId") Long customerId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("excludedBookingId") Long excludedBookingId
    );

    Page<Booking> findByStatusAndHoldExpiresAtLessThanEqualOrderByIdAsc(
            BookingStatus status,
            LocalDateTime expiresAt,
            Pageable pageable
    );

    Page<Booking> findByStatusAndPaymentExpiresAtLessThanEqualOrderByIdAsc(
            BookingStatus status,
            LocalDateTime expiresAt,
            Pageable pageable
    );

    @Query("""
            select b
            from Booking b
            where b.status = :status
              and b.showtime.endTime <= :endedAt
            order by b.id
            """)
    Page<Booking> findCompletionCandidates(
            @Param("status") BookingStatus status,
            @Param("endedAt") LocalDateTime endedAt,
            Pageable pageable
    );
}
