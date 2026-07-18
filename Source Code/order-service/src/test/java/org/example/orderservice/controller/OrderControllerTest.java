package org.example.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.dto.CreateOrderRequest;
import org.example.orderservice.dto.OrderItemRequest;
import org.example.orderservice.dto.OrderResponse;
import org.example.orderservice.model.OrderStatus;
import org.example.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    private OrderResponse buildResponse(Long id, OrderStatus status) {
        return new OrderResponse(id, "client@test.com", status,
                List.of(new OrderResponse.OrderItemResponse("Widget", 2, new BigDecimal("15.00"))),
                new BigDecimal("30.00"), Instant.now(), Instant.now());
    }

    @Test
    void createOrder_validRequest_shouldReturn201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "client@test.com",
                List.of(new OrderItemRequest("Widget", 2, new BigDecimal("15.00")))
        );
        when(orderService.createOrder(any())).thenReturn(buildResponse(1L, OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getById_existingId_shouldReturn200WithOrder() throws Exception {
        when(orderService.getById(1L)).thenReturn(buildResponse(1L, OrderStatus.PENDING));

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientEmail").value("client@test.com"));
    }

    @Test
    void getById_unknownId_shouldReturn404() throws Exception {
        when(orderService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: 99"));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByClient_shouldReturn200WithList() throws Exception {
        when(orderService.getByClientEmail("client@test.com"))
                .thenReturn(List.of(buildResponse(1L, OrderStatus.PENDING)));

        mockMvc.perform(get("/api/v1/orders/client/client@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void confirmOrder_shouldReturn200WithConfirmedStatus() throws Exception {
        when(orderService.confirmOrder(1L)).thenReturn(buildResponse(1L, OrderStatus.CONFIRMED));

        mockMvc.perform(patch("/api/v1/orders/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOrder_conflict_shouldReturn409() throws Exception {
        when(orderService.confirmOrder(1L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already confirmed"));

        mockMvc.perform(patch("/api/v1/orders/1/confirm"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_shouldReturn200WithCancelledStatus() throws Exception {
        when(orderService.cancelOrder(1L)).thenReturn(buildResponse(1L, OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
