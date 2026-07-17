package com.fpt.cinema.mapper;

import com.fpt.cinema.dto.response.AppliedVoucherResponse;
import com.fpt.cinema.dto.response.BookingComboResponse;
import com.fpt.cinema.dto.response.BookingHistoryItemResponse;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.dto.response.BookingSeatResponse;
import com.fpt.cinema.dto.response.BookingSummaryResponse;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.OrderCombo;
import com.fpt.cinema.entity.Payment;
import com.fpt.cinema.entity.Room;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.entity.Voucher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class BookingMapper {

    public BookingResponse toBookingResponse(
            Booking booking,
            List<Ticket> tickets,
            List<OrderCombo> orderCombos,
            LocalDateTime now
    ) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus().name(),
                booking.getShowtime().getShowtimeId(),
                toSeatResponses(tickets),
                toComboResponses(orderCombos),
                toAppliedVoucherResponse(booking),
                booking.getSeatSubtotal(),
                booking.getComboSubtotal(),
                booking.getDiscountAmount(),
                booking.getTotalAmount(),
                booking.getHoldExpiresAt(),
                remainingSeconds(now, booking.getHoldExpiresAt()),
                booking.getPaymentExpiresAt()
        );
    }

    public BookingHistoryItemResponse toHistoryItem(Booking booking, List<Ticket> tickets, Payment payment) {
        Showtime showtime = booking.getShowtime();
        Movie movie = showtime.getMovie();
        Room room = showtime.getRoom();
        Branch branch = room.getBranch();
        return new BookingHistoryItemResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus().name(),
                booking.getCreatedAt(),
                booking.getConfirmedAt(),
                booking.getCompletedAt(),
                booking.getCancelledAt(),
                booking.getExpiredAt(),
                new BookingHistoryItemResponse.MovieInfo(movie.getMovieId(), movie.getTitle(), movie.getPosterUrl(), movie.getAgeRating()),
                new BookingHistoryItemResponse.BranchInfo(branch.getBranchId(), branch.getBranchName(), branch.getAddress()),
                new BookingHistoryItemResponse.RoomInfo(room.getRoomId(), room.getRoomName(), room.getRoomType()),
                new BookingHistoryItemResponse.ShowtimeInfo(showtime.getShowtimeId(), showtime.getStartTime(), showtime.getEndTime()),
                tickets.stream().map(ticket -> ticket.getShowtimeSeat().getSeat().getSeatRow() + ticket.getShowtimeSeat().getSeat().getSeatNumber()).toList(),
                booking.getSeatSubtotal(),
                booking.getComboSubtotal(),
                booking.getDiscountAmount(),
                booking.getTotalAmount(),
                payment == null ? null : new BookingHistoryItemResponse.PaymentInfo(
                        payment.getStatus().name(), payment.getMethod(), payment.getPaymentTime(), payment.getTransactionCode()
                ),
                tickets.stream().map(ticket -> new BookingHistoryItemResponse.TicketInfo(
                        ticket.getId(), ticket.getTicketCode(), ticket.getTicketStatus().name(),
                        ticket.getShowtimeSeat().getSeat().getSeatRow() + ticket.getShowtimeSeat().getSeat().getSeatNumber(),
                        ticket.getPrice(), ticket.getCheckedInAt()
                )).toList()
        );
    }

    public BookingSummaryResponse toSummaryResponse(
            Booking booking,
            List<Ticket> tickets,
            List<OrderCombo> orderCombos,
            List<String> allowedActions
    ) {
        Showtime showtime = booking.getShowtime();
        Movie movie = showtime.getMovie();
        Room room = showtime.getRoom();
        Branch branch = room.getBranch();

        return new BookingSummaryResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus().name(),
                new BookingSummaryResponse.MovieInfo(
                        movie.getMovieId(),
                        movie.getTitle(),
                        movie.getPosterUrl(),
                        movie.getDuration(),
                        movie.getAgeRating()
                ),
                new BookingSummaryResponse.BranchInfo(
                        branch.getBranchId(),
                        branch.getBranchName(),
                        branch.getAddress()
                ),
                new BookingSummaryResponse.RoomInfo(
                        room.getRoomId(),
                        room.getRoomName(),
                        room.getRoomType()
                ),
                new BookingSummaryResponse.ShowtimeInfo(
                        showtime.getShowtimeId(),
                        showtime.getStartTime(),
                        showtime.getEndTime(),
                        showtime.getPrice()
                ),
                toSeatResponses(tickets),
                toComboResponses(orderCombos),
                toAppliedVoucherResponse(booking),
                booking.getSeatSubtotal(),
                booking.getComboSubtotal(),
                booking.getDiscountAmount(),
                booking.getTotalAmount(),
                booking.getHoldExpiresAt(),
                booking.getPaymentExpiresAt(),
                List.copyOf(allowedActions)
        );
    }

    private List<BookingSeatResponse> toSeatResponses(List<Ticket> tickets) {
        return tickets.stream()
                .sorted(Comparator.comparing(ticket -> ticket.getShowtimeSeat().getShowtimeSeatId()))
                .map(this::toSeatResponse)
                .toList();
    }

    private BookingSeatResponse toSeatResponse(Ticket ticket) {
        ShowtimeSeat showtimeSeat = ticket.getShowtimeSeat();
        var seat = showtimeSeat.getSeat();
        return new BookingSeatResponse(
                showtimeSeat.getShowtimeSeatId(),
                seat.getSeatId(),
                seat.getSeatRow() + seat.getSeatNumber(),
                seat.getSeatRow(),
                seat.getSeatNumber(),
                seat.getSeatType().getTypeName(),
                ticket.getPrice()
        );
    }

    private List<BookingComboResponse> toComboResponses(List<OrderCombo> orderCombos) {
        return orderCombos.stream()
                .sorted(Comparator.comparing(orderCombo -> orderCombo.getCombo().getId()))
                .map(orderCombo -> new BookingComboResponse(
                        orderCombo.getCombo().getId(),
                        orderCombo.getCombo().getName(),
                        orderCombo.getQuantity(),
                        orderCombo.getUnitPrice(),
                        orderCombo.getLineTotal()
                ))
                .toList();
    }

    private AppliedVoucherResponse toAppliedVoucherResponse(Booking booking) {
        Voucher voucher = booking.getAppliedVoucher();
        if (voucher == null) {
            return null;
        }
        return new AppliedVoucherResponse(
                voucher.getId(),
                voucher.getCode(),
                booking.getDiscountAmount()
        );
    }

    private long remainingSeconds(LocalDateTime now, LocalDateTime expiresAt) {
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, expiresAt).getSeconds());
    }
}
