package com.fpt.cinema.repository;

import com.fpt.cinema.entity.BranchInventory;
import com.fpt.cinema.entity.BranchInventoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchInventoryRepository extends JpaRepository<BranchInventory, BranchInventoryId> {
}
