package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.response.BranchShowtimesResponse;
import com.fpt.cinema.dto.response.MovieDetailResponse;
import com.fpt.cinema.dto.response.MovieSummaryResponse;
import com.fpt.cinema.service.MovieService;
import com.fpt.cinema.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/movies")
@Tag(name = "Movies", description = "Public movie catalogue and movie schedules")
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeService showtimeService;

    public MovieController(MovieService movieService, ShowtimeService showtimeService) {
        this.movieService = movieService;
        this.showtimeService = showtimeService;
    }

    @GetMapping
    @Operation(summary = "List active movies", description = "Public endpoint; optionally filters by title using q.")
    public ApiResponse<List<MovieSummaryResponse>> getMovies(
            @RequestParam(required = false) String q
    ) {
        return ApiResponse.success("Lấy danh sách phim thành công", movieService.getMovies(q));
    }

    @GetMapping("/{movieId}")
    @Operation(summary = "Get active movie details", description = "Public endpoint.")
    public ApiResponse<MovieDetailResponse> getMovieDetail(
            @PathVariable @Positive(message = "Mã phim phải lớn hơn 0") Long movieId
    ) {
        return ApiResponse.success("Lấy chi tiết phim thành công", movieService.getMovieDetail(movieId));
    }

    @GetMapping("/{movieId}/showtimes")
    @Operation(summary = "List a movie's showtimes grouped by branch", description = "Public endpoint filtered by date and optional branch.")
    public ApiResponse<List<BranchShowtimesResponse>> getMovieShowtimes(
            @PathVariable @Positive(message = "Mã phim phải lớn hơn 0") Long movieId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive(message = "Mã chi nhánh phải lớn hơn 0") Long branchId
    ) {
        return ApiResponse.success(
                "Lấy lịch chiếu theo phim thành công",
                showtimeService.getMovieShowtimes(movieId, date, branchId)
        );
    }
}
