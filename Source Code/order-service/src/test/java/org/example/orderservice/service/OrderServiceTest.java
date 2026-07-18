package org.example.orderservice.service;

import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.OrderItemRequest;
import org.example.orderservice.dto.OrderResponse;
import org.example.orderservice.kafka.OrderCancelledEvent;
import org.example.orderservice.kafka.OrderConfirmedEvent;
import org.example.orderservice.kafka.OrderEventProducer;
import org.example.orderservice.model.Order;
import org.example.orderservice.model.OrderItem;
import org.example.orderservice.model.OrderStatus;
import org.example.orderservice.repository.OrderRepository;
import org.example.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order buildOrder(Long id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .clientEmail("client@test.com")
                .status(status)
                .items(List.of(OrderItem.builder()
                        .productName("Widget")
                        .quantity(2)
                        .unitPrice(new BigDecimal("15.00"))
                        .build()))
                .totalAmount(new BigDecimal("30.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createOrder_shouldComputeTotalAndPersistWithStatusPending() {
        CreateOrderRequest request = new CreateOrderRequest(
                "client@test.com",
                List.of(
                        new OrderItemRequest("Widget", 2, new BigDecimal("15.00")),
                        new OrderItemRequest("Gadget", 1, new BigDecimal("20.00"))
                )
        );

        Order persisted = buildOrder(1L, OrderStatus.PENDING);
        persisted.setTotalAmount(new BigDecimal("50.00"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(captor.capture())).thenReturn(persisted);

        OrderResponse response = orderService.createOrder(request);

        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.clientEmail()).isEqualTo("client@test.com");
        verifyNoInteractions(orderEventProducer);
    }

    @Test
    void getById_shouldReturnOrderResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buildOrder(1L, OrderStatus.PENDING)));

        OrderResponse response = orderService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void getById_unknownId_shouldThrowNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getByClientEmail_shouldReturnAllOrdersForClient() {
        when(orderRepository.findByClientEmailOrderByCreatedAtDesc("client@test.com"))
                .thenReturn(List.of(buildOrder(1L, OrderStatus.PENDING), buildOrder(2L, OrderStatus.CONFIRMED)));

        List<OrderResponse> result = orderService.getByClientEmail("client@test.com");

        assertThat(result).hasSize(2);
    }

    @Test
    void confirmOrder_fromPending_shouldSetStatusConfirmedAndPublishEvent() {
        Order order = buildOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.confirmOrder(1L);

        ArgumentCaptor<OrderConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(orderEventProducer).publishOrderConfirmed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getClientEmail()).isEqualTo("client@test.com");
    }

    @Test
    void confirmOrder_alreadyConfirmed_shouldThrowConflict() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buildOrder(1L, OrderStatus.CONFIRMED)));

        assertThatThrownBy(() -> orderService.confirmOrder(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelOrder_fromPending_shouldSetStatusCancelledAndPublishEvent() {
        Order order = buildOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).publishOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
    }

    @Test
    void cancelOrder_fromConfirmed_shouldSetStatusCancelledAndPublishEvent() {
        Order order = buildOrder(1L, OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.cancelOrder(1L);

        verify(orderEventProducer).publishOrderCancelled(any());
    }

    @Test
    void cancelOrder_fromDelivered_shouldThrowConflict() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buildOrder(1L, OrderStatus.DELIVERED)));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelOrder_alreadyCancelled_shouldThrowConflict() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(buildOrder(1L, OrderStatus.CANCELLED)));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
