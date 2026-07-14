package com.fpt.cinema.repository;

import com.fpt.cinema.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByAccountAccountId(Long accountId);

    Optional<Customer> findByAccountUsernameIgnoreCase(String username);
}
