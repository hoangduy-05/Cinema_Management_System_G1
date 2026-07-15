package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingSummaryResponse(
        Long bookingId,
        String bookingCode,
        String status,
        MovieInfo movie,
        BranchInfo branch,
        RoomInfo room,
        ShowtimeInfo showtime,
        List<BookingSeatResponse> selectedSeats,
        List<BookingComboResponse> selectedCombos,
        AppliedVoucherResponse appliedVoucher,
        BigDecimal seatSubtotal,
        BigDecimal comboSubtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        LocalDateTime holdExpiresAt,
        LocalDateTime paymentExpiresAt,
        List<String> allowedActions
) {

    public record MovieInfo(
            Long movieId,
            String title,
            String posterUrl,
            Integer duration,
            String ageRating
    ) {
    }

    public record BranchInfo(
            Long branchId,
            String branchName,
            String address
    ) {
    }

    public record RoomInfo(
            Long roomId,
            String roomName,
            String roomType
    ) {
    }

    public record ShowtimeInfo(
            Long showtimeId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal basePrice
    ) {
    }
}
