package com.fpt.cinema.repository;

import com.fpt.cinema.entity.CinemaOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaOrderRepository extends JpaRepository<CinemaOrder, Long> {
}
