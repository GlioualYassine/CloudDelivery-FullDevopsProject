package org.example.deliveryservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.deliveryservice.dto.DriverRequest;
import org.example.deliveryservice.dto.DriverResponse;
import org.example.deliveryservice.service.DriverService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverService driverService;

    @InjectMocks
    private DriverController driverController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(driverController).build();
    }

    private DriverResponse buildResponse(Long id, boolean available) {
        return new DriverResponse(id, "John Driver", "driver@test.com", "+33600000000", available, Instant.now());
    }

    @Test
    void register_validRequest_shouldReturn201() throws Exception {
        DriverRequest request = new DriverRequest("John Driver", "driver@test.com", "+33600000000");
        when(driverService.register(any())).thenReturn(buildResponse(1L, true));

        mockMvc.perform(post("/api/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John Driver"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void getAll_shouldReturn200WithList() throws Exception {
        when(driverService.getAll()).thenReturn(List.of(buildResponse(1L, true), buildResponse(2L, false)));

        mockMvc.perform(get("/api/v1/drivers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAvailable_shouldReturn200WithAvailableDriversOnly() throws Exception {
        when(driverService.getAvailable()).thenReturn(List.of(buildResponse(1L, true)));

        mockMvc.perform(get("/api/v1/drivers/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void getById_unknownId_shouldReturn404() throws Exception {
        when(driverService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found: 99"));

        mockMvc.perform(get("/api/v1/drivers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setAvailability_shouldReturn200WithUpdatedStatus() throws Exception {
        when(driverService.setAvailability(1L, false)).thenReturn(buildResponse(1L, false));

        mockMvc.perform(patch("/api/v1/drivers/1/availability").param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
