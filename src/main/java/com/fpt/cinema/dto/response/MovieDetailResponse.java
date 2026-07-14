package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MovieDetailResponse(
        Long movieId,
        String title,
        String synopsis,
        String castText,
        Integer duration,
        LocalDate releaseDate,
        String language,
        String director,
        String ageRating,
        String status,
        String posterUrl,
        String trailerUrl,
        List<String> genres,
        BigDecimal averageRating,
        long reviewCount
) {
    public MovieDetailResponse {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
