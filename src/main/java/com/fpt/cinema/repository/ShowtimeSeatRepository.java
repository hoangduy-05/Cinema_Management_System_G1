package com.fpt.cinema.repository;

import com.fpt.cinema.entity.ShowtimeSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowtimeSeatRepository extends JpaRepository<ShowtimeSeat, Long> {

    List<ShowtimeSeat> findAllByShowtimeShowtimeId(Long showtimeId);

    @Query("""
            select ss
            from ShowtimeSeat ss
            join fetch ss.showtime sh
            join fetch ss.seat seat
            join fetch seat.seatType seatType
            where sh.showtimeId = :showtimeId
            order by seat.gridRow asc, seat.gridCol asc, seat.seatId asc
            """)
    List<ShowtimeSeat> findAllForSeatMap(@Param("showtimeId") Long showtimeId);

    List<ShowtimeSeat> findAllByBookingIdOrderByShowtimeSeatIdAsc(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select showtimeSeat
            from ShowtimeSeat showtimeSeat
            where showtimeSeat.showtime.showtimeId = :showtimeId
              and showtimeSeat.showtimeSeatId in :showtimeSeatIds
            order by showtimeSeat.showtimeSeatId
            """)
    List<ShowtimeSeat> findAllForUpdate(
            @Param("showtimeId") Long showtimeId,
            @Param("showtimeSeatIds") Collection<Long> showtimeSeatIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select showtimeSeat
            from ShowtimeSeat showtimeSeat
            where showtimeSeat.booking.id = :bookingId
            order by showtimeSeat.showtimeSeatId
            """)
    List<ShowtimeSeat> findAllByBookingIdForUpdate(@Param("bookingId") Long bookingId);
}
