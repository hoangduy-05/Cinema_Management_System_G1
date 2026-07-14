package com.fpt.cinema.repository;

import com.fpt.cinema.entity.ConcessionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcessionCategoryRepository extends JpaRepository<ConcessionCategory, Long> {
}
