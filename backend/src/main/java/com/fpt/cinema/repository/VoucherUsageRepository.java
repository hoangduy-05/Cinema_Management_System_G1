package com.fpt.cinema.repository;

import com.fpt.cinema.entity.VoucherUsage;
import com.fpt.cinema.entity.VoucherUsageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, VoucherUsageId> {

    List<VoucherUsage> findAllByOrderId(Long orderId);

    @Query("""
            select count(usage) > 0
            from VoucherUsage usage
            where usage.voucher.id = :voucherId
              and usage.order.id = :orderId
            """)
    boolean existsForVoucherAndOrder(
            @Param("voucherId") Long voucherId,
            @Param("orderId") Long orderId
    );

    @Query("""
            select count(usage)
            from VoucherUsage usage
            where usage.voucher.id = :voucherId
              and usage.order.customer.customerId = :customerId
            """)
    long countByVoucherAndCustomer(
            @Param("voucherId") Long voucherId,
            @Param("customerId") Long customerId
    );
}
