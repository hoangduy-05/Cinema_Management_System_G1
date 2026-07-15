package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voucher from Voucher voucher where voucher.id = :voucherId")
    Optional<Voucher> findByIdForUpdate(@Param("voucherId") Long voucherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voucher from Voucher voucher where lower(voucher.code) = lower(:code)")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);
}
