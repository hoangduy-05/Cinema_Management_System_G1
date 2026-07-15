package com.fpt.cinema.repository;

import com.fpt.cinema.entity.MovieGenre;
import com.fpt.cinema.entity.MovieGenreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenreId> {

    List<MovieGenre> findAllByMovieMovieId(Long movieId);
}
