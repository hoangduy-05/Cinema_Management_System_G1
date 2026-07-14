package com.fpt.cinema.entity;

import com.fpt.cinema.enums.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_order", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_booking_code", columnNames = "booking_code")
        },
        indexes = {
                @Index(name = "idx_booking_showtime", columnList = "showtime_id"),
                @Index(name = "idx_booking_status", columnList = "status"),
                @Index(name = "idx_booking_hold_expiry", columnList = "status,hold_expires_at"),
                @Index(name = "idx_booking_payment_expiry", columnList = "status,payment_expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 50)
    private String bookingCode;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private CinemaOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_voucher_id")
    private Voucher appliedVoucher;

    @Column(name = "booking_qr_code", length = 255)
    private String bookingQrCode;

    @Column(name = "booking_time", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ticket_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal seatSubtotal = BigDecimal.ZERO;

    @Column(name = "combo_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal comboSubtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "held_at")
    private LocalDateTime heldAt;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "payment_expires_at")
    private LocalDateTime paymentExpiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    @Setter(AccessLevel.NONE)
    private BookingStatus status = BookingStatus.CREATED;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    void applyStatus(BookingStatus status) {
        this.status = status;
    }

}
