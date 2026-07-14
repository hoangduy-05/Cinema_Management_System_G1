package com.fpt.cinema.service;

import com.fpt.cinema.dto.response.MovieDetailResponse;
import com.fpt.cinema.dto.response.MovieSummaryResponse;

import java.util.List;

public interface MovieService {

    List<MovieSummaryResponse> getMovies(String query);

    MovieDetailResponse getMovieDetail(Long movieId);
}
