package com.fpt.cinema.mapper;

import com.fpt.cinema.dto.response.MovieDetailResponse;
import com.fpt.cinema.dto.response.MovieSummaryResponse;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.MovieReview;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class MovieMapper {

    public MovieSummaryResponse toSummaryResponse(Movie movie) {
        if (movie == null) {
            return null;
        }

        return new MovieSummaryResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getDuration(),
                movie.getReleaseDate(),
                movie.getLanguage(),
                movie.getAgeRating(),
                movie.getStatus(),
                movie.getPosterUrl()
        );
    }

    public List<MovieSummaryResponse> toSummaryResponses(Collection<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            return List.of();
        }

        return movies.stream()
                .filter(Objects::nonNull)
                .map(this::toSummaryResponse)
                .toList();
    }

    public MovieDetailResponse toDetailResponse(Movie movie) {
        if (movie == null) {
            return null;
        }

        List<String> genres = movie.getMovieGenres() == null
                ? List.of()
                : movie.getMovieGenres().stream()
                        .filter(Objects::nonNull)
                        .map(movieGenre -> movieGenre.getGenre())
                        .filter(Objects::nonNull)
                        .map(genre -> genre.getName())
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

        List<BigDecimal> visibleRatings = movie.getReviews() == null
                ? List.of()
                : movie.getReviews().stream()
                        .filter(Objects::nonNull)
                        .filter(review -> isVisible(review) && review.getRating() != null)
                        .map(MovieReview::getRating)
                        .toList();

        BigDecimal averageRating = visibleRatings.isEmpty()
                ? null
                : visibleRatings.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(visibleRatings.size()), 1, RoundingMode.HALF_UP);

        return toDetailResponse(movie, genres, averageRating, visibleRatings.size());
    }

    public MovieDetailResponse toDetailResponse(
            Movie movie,
            List<String> genres,
            BigDecimal averageRating,
            long reviewCount
    ) {
        if (movie == null) {
            return null;
        }

        return new MovieDetailResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getSynopsis(),
                movie.getCastText(),
                movie.getDuration(),
                movie.getReleaseDate(),
                movie.getLanguage(),
                movie.getDirector(),
                movie.getAgeRating(),
                movie.getStatus(),
                movie.getPosterUrl(),
                movie.getTrailerUrl(),
                genres,
                averageRating,
                reviewCount
        );
    }

    private boolean isVisible(MovieReview review) {
        return review.getStatus() == null || "VISIBLE".equalsIgnoreCase(review.getStatus());
    }
}
