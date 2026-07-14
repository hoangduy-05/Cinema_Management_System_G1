package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long bookingId,
        String bookingCode,
        String status,
        Long showtimeId,
        List<BookingSeatResponse> selectedSeats,
        List<BookingComboResponse> selectedCombos,
        AppliedVoucherResponse appliedVoucher,
        BigDecimal seatSubtotal,
        BigDecimal comboSubtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        LocalDateTime holdExpiresAt,
        Long remainingHoldSeconds,
        LocalDateTime paymentExpiresAt
) {
}
