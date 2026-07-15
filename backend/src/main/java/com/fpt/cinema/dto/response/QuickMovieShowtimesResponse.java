package com.fpt.cinema.dto.response;

import java.util.List;

public record QuickMovieShowtimesResponse(
        MovieSummaryResponse movie,
        List<ShowtimeResponse> showtimes
) {
    public QuickMovieShowtimesResponse {
        showtimes = showtimes == null ? List.of() : List.copyOf(showtimes);
    }
}
