package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByStatusOrderByBranchNameAsc(String status);

    boolean existsByBranchIdAndStatus(Long branchId, String status);
}
