package org.example.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.OrderResponse;
import org.example.orderservice.kafka.OrderCancelledEvent;
import org.example.orderservice.kafka.OrderConfirmedEvent;
import org.example.orderservice.kafka.OrderEventProducer;
import org.example.orderservice.model.Order;
import org.example.orderservice.model.OrderItem;
import org.example.orderservice.model.OrderStatus;
import org.example.orderservice.repository.OrderRepository;
import org.example.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> OrderItem.builder()
                        .productName(i.productName())
                        .quantity(i.quantity())
                        .unitPrice(i.unitPrice())
                        .build())
                .toList();

        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .clientEmail(request.clientEmail())
                .status(OrderStatus.PENDING)
                .items(items)
                .totalAmount(total)
                .build();

        Order saved = orderRepository.save(order);
        log.info("Created order id={} for client={}", saved.getId(), saved.getClientEmail());
        return OrderResponse.from(saved);
    }

    @Override
    public OrderResponse getById(Long id) {
        return OrderResponse.from(findOrThrow(id));
    }

    @Override
    public List<OrderResponse> getByClientEmail(String email) {
        return orderRepository.findByClientEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(Long id) {
        Order order = findOrThrow(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + id + " cannot be confirmed from status " + order.getStatus());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);

        orderEventProducer.publishOrderConfirmed(OrderConfirmedEvent.builder()
                .orderId(saved.getId())
                .clientEmail(saved.getClientEmail())
                .totalAmount(saved.getTotalAmount())
                .confirmedAt(Instant.now().toString())
                .build());

        return OrderResponse.from(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = findOrThrow(id);
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + id + " cannot be cancelled from status " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        orderEventProducer.publishOrderCancelled(OrderCancelledEvent.builder()
                .orderId(saved.getId())
                .clientEmail(saved.getClientEmail())
                .cancelledAt(Instant.now().toString())
                .build());

        return OrderResponse.from(saved);
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order not found: " + id));
    }
}
