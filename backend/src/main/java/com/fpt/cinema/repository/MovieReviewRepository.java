package com.fpt.cinema.repository;

import com.fpt.cinema.entity.MovieReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieReviewRepository extends JpaRepository<MovieReview, Long> {

    List<MovieReview> findAllByMovieMovieId(Long movieId);
}
