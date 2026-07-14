package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.response.MovieDetailResponse;
import com.fpt.cinema.dto.response.MovieSummaryResponse;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.mapper.MovieMapper;
import com.fpt.cinema.repository.MovieRepository;
import com.fpt.cinema.service.MovieService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final MovieMapper movieMapper;

    public MovieServiceImpl(MovieRepository movieRepository, MovieMapper movieMapper) {
        this.movieRepository = movieRepository;
        this.movieMapper = movieMapper;
    }

    @Override
    public List<MovieSummaryResponse> getMovies(String query) {
        String normalizedQuery = normalizeQuery(query);
        return movieMapper.toSummaryResponses(movieRepository.findActiveMovies(normalizedQuery));
    }

    @Override
    public MovieDetailResponse getMovieDetail(Long movieId) {
        Movie movie = movieRepository.findActiveMovieDetail(movieId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phim đang hoạt động với mã " + movieId
                ));
        return movieMapper.toDetailResponse(movie);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }
}
