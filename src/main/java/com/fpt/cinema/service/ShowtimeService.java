package com.fpt.cinema.service;

import com.fpt.cinema.dto.response.BranchShowtimesResponse;
import com.fpt.cinema.dto.response.QuickMovieShowtimesResponse;
import com.fpt.cinema.dto.response.ShowtimeDateResponse;
import com.fpt.cinema.dto.response.ShowtimeDetailResponse;
import com.fpt.cinema.dto.response.ShowtimeResponse;
import com.fpt.cinema.dto.response.ShowtimeSeatResponse;

import java.time.LocalDate;
import java.util.List;

public interface ShowtimeService {

    List<ShowtimeResponse> getShowtimes(Long movieId, LocalDate date, Long branchId);

    ShowtimeDetailResponse getShowtime(Long showtimeId);

    List<ShowtimeSeatResponse> getShowtimeSeats(Long showtimeId);

    List<ShowtimeDateResponse> getAvailableDates(Long movieId, Long branchId);

    List<BranchShowtimesResponse> getMovieShowtimes(Long movieId, LocalDate date, Long branchId);

    List<QuickMovieShowtimesResponse> getQuickShowtimes(Long branchId, LocalDate date);
}
