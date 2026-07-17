package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Payment;
import com.fpt.cinema.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionCode(String transactionCode);

    List<Payment> findAllByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusAndExpiresAtAfterOrderByIdDesc(
            Long orderId,
            PaymentStatus status,
            LocalDateTime now
    );

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Page<Payment> findByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            PaymentStatus status,
            LocalDateTime expiresAt,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.transactionCode = :transactionCode")
    Optional<Payment> findByTransactionCodeForUpdate(@Param("transactionCode") String transactionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.order.id = :orderId order by payment.id")
    List<Payment> findAllByOrderIdForUpdate(@Param("orderId") Long orderId);
}
