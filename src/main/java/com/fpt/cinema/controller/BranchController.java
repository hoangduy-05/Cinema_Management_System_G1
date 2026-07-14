package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.response.BranchResponse;
import com.fpt.cinema.dto.response.QuickMovieShowtimesResponse;
import com.fpt.cinema.service.BranchService;
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
@RequestMapping("/api/v1/branches")
@Tag(name = "Branches", description = "Public cinema branches and quick schedules")
public class BranchController {

    private final BranchService branchService;
    private final ShowtimeService showtimeService;

    public BranchController(BranchService branchService, ShowtimeService showtimeService) {
        this.branchService = branchService;
        this.showtimeService = showtimeService;
    }

    @GetMapping
    @Operation(summary = "List active cinema branches", description = "Public endpoint.")
    public ApiResponse<List<BranchResponse>> getBranches() {
        return ApiResponse.success("Lấy danh sách chi nhánh thành công", branchService.getBranches());
    }

    @GetMapping("/{branchId}/showtimes/quick")
    @Operation(summary = "Get a branch's quick schedule", description = "Public endpoint grouped by movie for the requested date.")
    public ApiResponse<List<QuickMovieShowtimesResponse>> getQuickShowtimes(
            @PathVariable @Positive(message = "Mã chi nhánh phải lớn hơn 0") Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(
                "Lấy lịch chiếu nhanh theo rạp thành công",
                showtimeService.getQuickShowtimes(branchId, date)
        );
    }
}
