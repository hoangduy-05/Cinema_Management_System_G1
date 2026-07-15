package com.fpt.cinema.repository;

import com.fpt.cinema.entity.OrderConcession;
import com.fpt.cinema.entity.OrderConcessionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderConcessionRepository extends JpaRepository<OrderConcession, OrderConcessionId> {
}
