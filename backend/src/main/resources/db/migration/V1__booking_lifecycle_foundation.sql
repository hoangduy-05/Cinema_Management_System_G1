-- Booking lifecycle columns required by the customer booking flow.
-- The original schema remains authoritative: booking_time is reused as created_at,
-- and ticket_subtotal is reused as the seat subtotal.

ALTER TABLE booking
    ADD COLUMN booking_code VARCHAR(50) NULL AFTER booking_id,
    ADD COLUMN applied_voucher_id BIGINT NULL AFTER showtime_id,
    ADD COLUMN combo_subtotal DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER ticket_subtotal,
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER combo_subtotal,
    ADD COLUMN total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER discount_amount,
    ADD COLUMN held_at DATETIME NULL AFTER total_amount,
    ADD COLUMN hold_expires_at DATETIME NULL AFTER held_at,
    ADD COLUMN payment_expires_at DATETIME NULL AFTER hold_expires_at,
    ADD COLUMN confirmed_at DATETIME NULL AFTER payment_expires_at,
    ADD COLUMN completed_at DATETIME NULL AFTER confirmed_at,
    ADD COLUMN cancelled_at DATETIME NULL AFTER completed_at,
    ADD COLUMN expired_at DATETIME NULL AFTER cancelled_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER expired_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE showtime_seat
    ADD COLUMN booking_id BIGINT NULL AFTER seat_id,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER locked_until;

ALTER TABLE ticket
    ADD COLUMN ticket_code VARCHAR(50) NULL AFTER ticket_id,
    ADD COLUMN qr_token VARCHAR(255) NULL AFTER ticket_code;

ALTER TABLE payment
    ADD COLUMN expires_at DATETIME NULL AFTER payment_time,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE voucher
    ADD COLUMN minimum_order_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER discount_value,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

-- Normalize historical values that are outside the customer-booking lifecycle.
-- This keeps the runtime enums strict without adding unsupported transitions.
UPDATE booking
SET status = 'COMPLETED',
    completed_at = COALESCE(completed_at, booking_time)
WHERE status NOT IN (
    'CREATED', 'SEAT_HELD', 'PENDING_PAYMENT', 'CONFIRMED',
    'COMPLETED', 'CANCELLED', 'EXPIRED'
);

UPDATE payment
SET status = CASE WHEN payment_time IS NULL THEN 'FAILED' ELSE 'SUCCESS' END
WHERE status NOT IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED');

UPDATE ticket
SET ticket_status = 'CANCELLED'
WHERE ticket_status NOT IN ('HELD', 'VALID', 'USED', 'CANCELLED', 'EXPIRED');

UPDATE `order` orderRow
JOIN booking bookingRow ON bookingRow.order_id = orderRow.order_id
SET orderRow.status = bookingRow.status,
    orderRow.payment_status = CASE
        WHEN bookingRow.status IN ('CONFIRMED', 'COMPLETED') THEN 'PAID'
        WHEN bookingRow.status = 'PENDING_PAYMENT' THEN 'PENDING'
        WHEN bookingRow.status IN ('CANCELLED', 'EXPIRED') THEN 'EXPIRED'
        ELSE orderRow.payment_status
    END
WHERE orderRow.status NOT IN (
    'CREATED', 'SEAT_HELD', 'PENDING_PAYMENT', 'CONFIRMED',
    'COMPLETED', 'CANCELLED', 'EXPIRED'
);

-- Backfill deterministic identifiers for rows created by the supplied seed scripts.
UPDATE booking
SET booking_code = CONCAT('BK-LEGACY-', booking_id)
WHERE booking_code IS NULL OR booking_code = '';

UPDATE ticket
SET ticket_code = CONCAT('TKT-LEGACY-', ticket_id),
    qr_token = SHA2(CONCAT('legacy-ticket:', ticket_id, ':', booking_id, ':', showtime_seat_id), 256)
WHERE ticket_code IS NULL OR qr_token IS NULL;

-- A LOCKED row is the existing database equivalent of the domain status HELD.
-- Use the latest HELD ticket as the current owner when legacy seed rows are present.
UPDATE showtime_seat showtimeSeat
JOIN (
    SELECT ticket.showtime_seat_id, MAX(ticket.booking_id) AS booking_id
    FROM ticket
    WHERE ticket.ticket_status = 'HELD'
    GROUP BY ticket.showtime_seat_id
) heldTicket ON heldTicket.showtime_seat_id = showtimeSeat.showtime_seat_id
SET showtimeSeat.booking_id = heldTicket.booking_id
WHERE showtimeSeat.seat_status = 'LOCKED'
  AND showtimeSeat.booking_id IS NULL;

-- Reconstruct current totals from the existing order detail and finalized voucher tables.
UPDATE booking bookingRow
LEFT JOIN (
    SELECT order_id, SUM(line_total) AS combo_total
    FROM order_combo
    GROUP BY order_id
) comboTotals ON comboTotals.order_id = bookingRow.order_id
LEFT JOIN (
    SELECT order_id,
           MIN(voucher_id) AS voucher_id,
           SUM(discount_amount) AS discount_total
    FROM voucher_usage
    GROUP BY order_id
) voucherTotals ON voucherTotals.order_id = bookingRow.order_id
SET bookingRow.combo_subtotal = COALESCE(comboTotals.combo_total, 0.00),
    bookingRow.discount_amount = COALESCE(voucherTotals.discount_total, 0.00),
    bookingRow.applied_voucher_id = voucherTotals.voucher_id,
    bookingRow.total_amount = GREATEST(
            0.00,
            bookingRow.ticket_subtotal
                + COALESCE(comboTotals.combo_total, 0.00)
                - COALESCE(voucherTotals.discount_total, 0.00)
        ),
    bookingRow.updated_at = bookingRow.booking_time;

UPDATE booking bookingRow
LEFT JOIN (
    SELECT booking_id, MAX(locked_until) AS locked_until
    FROM showtime_seat
    WHERE booking_id IS NOT NULL
    GROUP BY booking_id
) heldSeats ON heldSeats.booking_id = bookingRow.booking_id
SET bookingRow.held_at = CASE
        WHEN bookingRow.status IN ('SEAT_HELD', 'PENDING_PAYMENT') THEN bookingRow.booking_time
        ELSE bookingRow.held_at
    END,
    bookingRow.hold_expires_at = CASE
        WHEN bookingRow.status IN ('SEAT_HELD', 'PENDING_PAYMENT') THEN heldSeats.locked_until
        ELSE bookingRow.hold_expires_at
    END,
    bookingRow.payment_expires_at = CASE
        WHEN bookingRow.status = 'PENDING_PAYMENT' THEN heldSeats.locked_until
        ELSE bookingRow.payment_expires_at
    END,
    bookingRow.confirmed_at = CASE
        WHEN bookingRow.status IN ('CONFIRMED', 'COMPLETED') THEN bookingRow.booking_time
        ELSE bookingRow.confirmed_at
    END,
    bookingRow.completed_at = CASE
        WHEN bookingRow.status = 'COMPLETED' THEN bookingRow.booking_time
        ELSE bookingRow.completed_at
    END,
    bookingRow.cancelled_at = CASE
        WHEN bookingRow.status = 'CANCELLED' THEN bookingRow.booking_time
        ELSE bookingRow.cancelled_at
    END,
    bookingRow.expired_at = CASE
        WHEN bookingRow.status = 'EXPIRED' THEN bookingRow.booking_time
        ELSE bookingRow.expired_at
    END;

UPDATE payment paymentRow
JOIN booking bookingRow ON bookingRow.order_id = paymentRow.order_id
SET paymentRow.expires_at = bookingRow.payment_expires_at
WHERE paymentRow.status = 'PENDING'
  AND paymentRow.expires_at IS NULL;

ALTER TABLE booking
    MODIFY COLUMN booking_code VARCHAR(50) NOT NULL;

ALTER TABLE ticket
    MODIFY COLUMN ticket_code VARCHAR(50) NOT NULL,
    MODIFY COLUMN qr_token VARCHAR(255) NOT NULL;

ALTER TABLE booking
    ADD CONSTRAINT uk_booking_code UNIQUE (booking_code),
    ADD KEY idx_booking_applied_voucher (applied_voucher_id),
    ADD KEY idx_booking_hold_expiry (status, hold_expires_at),
    ADD KEY idx_booking_payment_expiry (status, payment_expires_at),
    ADD CONSTRAINT fk_booking_applied_voucher
        FOREIGN KEY (applied_voucher_id) REFERENCES voucher(voucher_id)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE showtime_seat
    ADD KEY idx_showtime_seat_booking_status (booking_id, seat_status),
    ADD CONSTRAINT fk_showtime_seat_booking
        FOREIGN KEY (booking_id) REFERENCES booking(booking_id)
        ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE ticket
    ADD CONSTRAINT uk_ticket_code UNIQUE (ticket_code),
    ADD CONSTRAINT uk_ticket_qr_token UNIQUE (qr_token),
    ADD CONSTRAINT uk_ticket_booking_showtime_seat UNIQUE (booking_id, showtime_seat_id);

ALTER TABLE payment
    ADD KEY idx_payment_expiry (status, expires_at);
