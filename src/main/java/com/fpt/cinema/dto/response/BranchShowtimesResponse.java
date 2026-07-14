package com.fpt.cinema.dto.response;

import java.util.List;

public record BranchShowtimesResponse(
        BranchResponse branch,
        List<ShowtimeResponse> showtimes
) {
    public BranchShowtimesResponse {
        showtimes = showtimes == null ? List.of() : List.copyOf(showtimes);
    }
}
