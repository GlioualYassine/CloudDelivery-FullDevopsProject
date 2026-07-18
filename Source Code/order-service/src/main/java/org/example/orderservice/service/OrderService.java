package org.example.orderservice.service;

import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    OrderResponse getById(Long id);

    List<OrderResponse> getByClientEmail(String email);

    OrderResponse confirmOrder(Long id);

    OrderResponse cancelOrder(Long id);
}
