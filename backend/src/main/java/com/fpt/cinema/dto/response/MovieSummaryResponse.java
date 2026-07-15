package com.fpt.cinema.dto.response;

import java.time.LocalDate;

public record MovieSummaryResponse(
        Long movieId,
        String title,
        Integer duration,
        LocalDate releaseDate,
        String language,
        String ageRating,
        String status,
        String posterUrl
) {
}
