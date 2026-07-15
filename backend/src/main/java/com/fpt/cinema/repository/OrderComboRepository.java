package com.fpt.cinema.repository;

import com.fpt.cinema.entity.OrderCombo;
import com.fpt.cinema.entity.OrderComboId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderComboRepository extends JpaRepository<OrderCombo, OrderComboId> {

    List<OrderCombo> findAllByOrderIdOrderByComboIdAsc(Long orderId);

    void deleteAllByOrderId(Long orderId);
}
