package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.response.ShowtimeDateResponse;
import com.fpt.cinema.dto.response.ShowtimeDetailResponse;
import com.fpt.cinema.dto.response.ShowtimeResponse;
import com.fpt.cinema.dto.response.ShowtimeSeatResponse;
import com.fpt.cinema.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/showtimes")
@Tag(name = "Showtimes", description = "Public showtime discovery and seat availability")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    @Operation(summary = "Browse future showtimes", description = "Public endpoint filtered by optional movie, date and branch.")
    public ApiResponse<List<ShowtimeResponse>> getShowtimes(
            @RequestParam(required = false) @Positive(message = "Mã phim phải lớn hơn 0") Long movieId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive(message = "Mã chi nhánh phải lớn hơn 0") Long branchId
    ) {
        return ApiResponse.success(
                "Lấy danh sách lịch chiếu thành công",
                showtimeService.getShowtimes(movieId, date, branchId)
        );
    }

    @GetMapping("/{showtimeId}")
    @Operation(summary = "Get showtime details", description = "Public endpoint returning movie, branch and room information.")
    public ApiResponse<ShowtimeDetailResponse> getShowtime(
            @PathVariable @Positive(message = "Mã lịch chiếu phải lớn hơn 0") Long showtimeId
    ) {
        return ApiResponse.success(
                "Lấy chi tiết lịch chiếu thành công",
                showtimeService.getShowtime(showtimeId)
        );
    }

    @GetMapping("/{showtimeId}/seats")
    @Operation(summary = "Get showtime seat map", description = "Public endpoint; exposes logical availability and server-calculated prices without hold ownership.")
    public ApiResponse<List<ShowtimeSeatResponse>> getShowtimeSeats(
            @PathVariable @Positive(message = "Mã lịch chiếu phải lớn hơn 0") Long showtimeId
    ) {
        return ApiResponse.success(
                "Lấy sơ đồ ghế thành công",
                showtimeService.getShowtimeSeats(showtimeId)
        );
    }

    @GetMapping("/dates")
    @Operation(summary = "List dates with future showtimes", description = "Public endpoint filtered by optional movie and branch.")
    public ApiResponse<List<ShowtimeDateResponse>> getAvailableDates(
            @RequestParam(required = false) @Positive(message = "Mã phim phải lớn hơn 0") Long movieId,
            @RequestParam(required = false) @Positive(message = "Mã chi nhánh phải lớn hơn 0") Long branchId
    ) {
        return ApiResponse.success(
                "Lấy danh sách ngày có lịch chiếu thành công",
                showtimeService.getAvailableDates(movieId, branchId)
        );
    }
}
