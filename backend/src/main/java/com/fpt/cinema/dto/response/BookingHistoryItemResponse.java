package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingHistoryItemResponse(
        Long bookingId,
        String bookingCode,
        String bookingStatus,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        LocalDateTime expiredAt,
        MovieInfo movie,
        BranchInfo branch,
        RoomInfo room,
        ShowtimeInfo showtime,
        List<String> seatLabels,
        BigDecimal seatSubtotal,
        BigDecimal comboSubtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        PaymentInfo payment,
        List<TicketInfo> tickets
) {
    public record MovieInfo(Long movieId, String title, String posterUrl, String ageRating) {}
    public record BranchInfo(Long branchId, String branchName, String address) {}
    public record RoomInfo(Long roomId, String roomName, String roomType) {}
    public record ShowtimeInfo(Long showtimeId, LocalDateTime startTime, LocalDateTime endTime) {}
    public record PaymentInfo(String status, String method, LocalDateTime paidAt, String transactionReference) {}
    public record TicketInfo(Long ticketId, String ticketCode, String status, String seatLabel, BigDecimal price, LocalDateTime checkedInAt) {}
}
