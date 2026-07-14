package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    @Query("""
            select s
            from Showtime s
            join fetch s.movie m
            join fetch s.room r
            join fetch r.branch b
            where s.status = 'AVAILABLE'
              and m.status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and b.status = 'ACTIVE'
              and s.startTime >= :rangeStart
              and (:rangeEnd is null or s.startTime < :rangeEnd)
              and (:movieId is null or m.movieId = :movieId)
              and (:branchId is null or b.branchId = :branchId)
            order by s.startTime asc, b.branchName asc, r.roomName asc
            """)
    List<Showtime> findForBrowse(
            @Param("movieId") Long movieId,
            @Param("branchId") Long branchId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

    @Query("""
            select s
            from Showtime s
            join fetch s.movie m
            join fetch s.room r
            join fetch r.branch b
            where s.showtimeId = :showtimeId
              and s.status = 'AVAILABLE'
              and m.status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and b.status = 'ACTIVE'
            """)
    Optional<Showtime> findPublicDetailById(@Param("showtimeId") Long showtimeId);

    @Query("""
            select s.startTime
            from Showtime s
            join s.movie m
            join s.room r
            join r.branch b
            where s.status = 'AVAILABLE'
              and m.status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and b.status = 'ACTIVE'
              and s.startTime >= :now
              and (:movieId is null or m.movieId = :movieId)
              and (:branchId is null or b.branchId = :branchId)
            order by s.startTime asc
            """)
    List<LocalDateTime> findAvailableStartTimes(
            @Param("movieId") Long movieId,
            @Param("branchId") Long branchId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select s
            from Showtime s
            join fetch s.movie m
            join fetch s.room r
            join fetch r.branch b
            where s.status = 'AVAILABLE'
              and m.status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and b.status = 'ACTIVE'
              and m.movieId = :movieId
              and s.startTime >= :startOfDay
              and s.startTime < :nextDay
              and (:branchId is null or b.branchId = :branchId)
            order by b.branchName asc, s.startTime asc
            """)
    List<Showtime> findAvailableByMovieAndDate(
            @Param("movieId") Long movieId,
            @Param("branchId") Long branchId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay
    );

    @Query("""
            select s
            from Showtime s
            join fetch s.movie m
            join fetch s.room r
            join fetch r.branch b
            where s.status = 'AVAILABLE'
              and m.status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and b.status = 'ACTIVE'
              and b.branchId = :branchId
              and s.startTime >= :startOfDay
              and s.startTime < :nextDay
            order by m.title asc, s.startTime asc
            """)
    List<Showtime> findAvailableByBranchAndDate(
            @Param("branchId") Long branchId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("nextDay") LocalDateTime nextDay
    );
}
