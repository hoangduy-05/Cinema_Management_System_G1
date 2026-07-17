package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Combo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    List<Combo> findAllByStatusIgnoreCaseOrderByIdAsc(String status);
}
