package org.example.deliveryservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.deliveryservice.dto.AssignDriverRequest;
import org.example.deliveryservice.dto.DeliveryResponse;
import org.example.deliveryservice.model.DeliveryStatus;
import org.example.deliveryservice.service.DeliveryService;
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

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private DeliveryController deliveryController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deliveryController).build();
    }

    private DeliveryResponse buildResponse(Long id, DeliveryStatus status) {
        return new DeliveryResponse(id, 10L, "client@test.com", null, status,
                Instant.now(), Instant.now(), null);
    }

    @Test
    void getById_existingId_shouldReturn200() throws Exception {
        when(deliveryService.getById(1L)).thenReturn(buildResponse(1L, DeliveryStatus.PENDING_ASSIGNMENT));

        mockMvc.perform(get("/api/v1/deliveries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING_ASSIGNMENT"));
    }

    @Test
    void getById_unknownId_shouldReturn404() throws Exception {
        when(deliveryService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found: 99"));

        mockMvc.perform(get("/api/v1/deliveries/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByOrderId_shouldReturn200() throws Exception {
        when(deliveryService.getByOrderId(10L)).thenReturn(buildResponse(1L, DeliveryStatus.PENDING_ASSIGNMENT));

        mockMvc.perform(get("/api/v1/deliveries/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientEmail").value("client@test.com"));
    }

    @Test
    void assignDriver_validRequest_shouldReturn200() throws Exception {
        AssignDriverRequest request = new AssignDriverRequest(5L);
        when(deliveryService.assignDriver(1L, 5L)).thenReturn(buildResponse(1L, DeliveryStatus.ASSIGNED));

        mockMvc.perform(patch("/api/v1/deliveries/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void pickUp_shouldReturn200() throws Exception {
        when(deliveryService.markPickedUp(1L)).thenReturn(buildResponse(1L, DeliveryStatus.PICKED_UP));

        mockMvc.perform(patch("/api/v1/deliveries/1/pickup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void complete_shouldReturn200WithDeliveredStatus() throws Exception {
        when(deliveryService.markDelivered(1L)).thenReturn(buildResponse(1L, DeliveryStatus.DELIVERED));

        mockMvc.perform(patch("/api/v1/deliveries/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void fail_conflict_shouldReturn409() throws Exception {
        when(deliveryService.markFailed(1L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already in terminal state"));

        mockMvc.perform(patch("/api/v1/deliveries/1/fail"))
                .andExpect(status().isConflict());
    }
}
