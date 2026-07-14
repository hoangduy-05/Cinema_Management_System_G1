package com.fpt.cinema.mapper;

import com.fpt.cinema.dto.response.ShowtimeResponse;
import com.fpt.cinema.dto.response.ShowtimeDetailResponse;
import com.fpt.cinema.dto.response.ShowtimeSeatResponse;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.Room;
import com.fpt.cinema.entity.Seat;
import com.fpt.cinema.entity.SeatType;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class ShowtimeMapper {

    public ShowtimeResponse toResponse(Showtime showtime) {
        if (showtime == null) {
            return null;
        }

        Movie movie = showtime.getMovie();
        Room room = showtime.getRoom();
        Branch branch = room == null ? null : room.getBranch();

        return new ShowtimeResponse(
                showtime.getShowtimeId(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getPrice(),
                showtime.getStatus(),
                movie == null ? null : movie.getMovieId(),
                movie == null ? null : movie.getTitle(),
                movie == null ? null : movie.getPosterUrl(),
                movie == null ? null : movie.getDuration(),
                branch == null ? null : branch.getBranchId(),
                branch == null ? null : branch.getBranchName(),
                branch == null ? null : branch.getAddress(),
                room == null ? null : room.getRoomId(),
                room == null ? null : room.getRoomName(),
                room == null ? null : room.getRoomType()
        );
    }

    public List<ShowtimeResponse> toResponses(Collection<Showtime> showtimes) {
        if (showtimes == null || showtimes.isEmpty()) {
            return List.of();
        }

        return showtimes.stream()
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }

    public ShowtimeDetailResponse toDetailResponse(Showtime showtime) {
        if (showtime == null) {
            return null;
        }

        Movie movie = showtime.getMovie();
        Room room = showtime.getRoom();
        Branch branch = room == null ? null : room.getBranch();

        return new ShowtimeDetailResponse(
                showtime.getShowtimeId(),
                movie == null ? null : movie.getMovieId(),
                movie == null ? null : movie.getTitle(),
                movie == null ? null : movie.getPosterUrl(),
                movie == null ? null : movie.getDuration(),
                branch == null ? null : branch.getBranchId(),
                branch == null ? null : branch.getBranchName(),
                branch == null ? null : branch.getAddress(),
                room == null ? null : room.getRoomId(),
                room == null ? null : room.getRoomName(),
                room == null ? null : room.getRoomType(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getPrice(),
                showtime.getStatus()
        );
    }

    public ShowtimeSeatResponse toSeatResponse(ShowtimeSeat showtimeSeat, boolean selectable) {
        if (showtimeSeat == null) {
            return null;
        }

        Seat seat = showtimeSeat.getSeat();
        SeatType seatType = seat == null ? null : seat.getSeatType();
        BigDecimal price = calculateSeatPrice(showtimeSeat.getShowtime(), seatType);
        String seatRow = seat == null ? null : seat.getSeatRow();
        String seatNumber = seat == null ? null : seat.getSeatNumber();

        return new ShowtimeSeatResponse(
                showtimeSeat.getShowtimeSeatId(),
                seat == null ? null : seat.getSeatId(),
                seatRow == null || seatNumber == null ? null : seatRow + seatNumber,
                seatRow,
                seatNumber,
                seat == null ? null : seat.getGridRow(),
                seat == null ? null : seat.getGridCol(),
                seatType == null ? null : seatType.getTypeName(),
                price,
                showtimeSeat.getSeatStatus() == null ? null : showtimeSeat.getSeatStatus().name(),
                selectable
        );
    }

    private BigDecimal calculateSeatPrice(Showtime showtime, SeatType seatType) {
        if (showtime == null || showtime.getPrice() == null
                || seatType == null || seatType.getPriceMultiplier() == null) {
            return null;
        }
        return showtime.getPrice()
                .multiply(seatType.getPriceMultiplier())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
