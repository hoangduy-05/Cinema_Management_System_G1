package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByBookingIdOrderByIdAsc(Long bookingId);

    Optional<Ticket> findByTicketCode(String ticketCode);

    Optional<Ticket> findByQrToken(String qrToken);

    boolean existsByBookingIdAndShowtimeSeatShowtimeSeatId(Long bookingId, Long showtimeSeatId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.booking.id = :bookingId order by ticket.id")
    List<Ticket> findAllByBookingIdForUpdate(@Param("bookingId") Long bookingId);
}
