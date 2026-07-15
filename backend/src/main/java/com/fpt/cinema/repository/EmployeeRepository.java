package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByAccountAccountId(Long accountId);
}
