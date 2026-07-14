package com.fpt.cinema.repository;

import com.fpt.cinema.entity.ComboItem;
import com.fpt.cinema.entity.ComboItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboItemRepository extends JpaRepository<ComboItem, ComboItemId> {
}
