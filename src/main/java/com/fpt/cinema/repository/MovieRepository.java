package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Movie;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findAllByStatusOrderByReleaseDateDesc(String status);

    boolean existsByMovieIdAndStatus(Long movieId, String status);

    @Query("""
            select m
            from Movie m
            where m.status = 'ACTIVE'
              and (:query is null or lower(m.title) like lower(concat('%', :query, '%')))
            order by m.releaseDate desc, m.title asc
            """)
    List<Movie> findActiveMovies(@Param("query") String query);

    @EntityGraph(attributePaths = {"movieGenres.genre", "reviews"})
    @Query("""
            select distinct m
            from Movie m
            where m.movieId = :movieId
              and m.status = 'ACTIVE'
            """)
    Optional<Movie> findActiveMovieDetail(@Param("movieId") Long movieId);
}
