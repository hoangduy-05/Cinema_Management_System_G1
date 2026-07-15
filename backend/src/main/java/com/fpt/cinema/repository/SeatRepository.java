package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByRoomRoomIdOrderByGridRowAscGridColAsc(Long roomId);
}
