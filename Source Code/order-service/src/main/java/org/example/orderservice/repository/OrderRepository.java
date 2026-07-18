package org.example.orderservice.repository;

import org.example.orderservice.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientEmailOrderByCreatedAtDesc(String clientEmail);
}
