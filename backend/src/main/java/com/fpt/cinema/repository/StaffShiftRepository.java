package com.fpt.cinema.repository;

import com.fpt.cinema.entity.StaffShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long> {

    List<StaffShift> findAllByEmployeeEmployeeId(Long employeeId);
}
