package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByBranchBranchIdAndStatus(Long branchId, String status);
}
