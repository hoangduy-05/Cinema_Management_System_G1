package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Concession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcessionRepository extends JpaRepository<Concession, Long> {
}
