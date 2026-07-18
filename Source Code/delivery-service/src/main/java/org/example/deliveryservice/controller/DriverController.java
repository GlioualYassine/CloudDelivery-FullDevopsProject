package org.example.deliveryservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deliveryservice.dto.DriverRequest;
import org.example.deliveryservice.dto.DriverResponse;
import org.example.deliveryservice.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    public ResponseEntity<DriverResponse> register(@Valid @RequestBody DriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<DriverResponse>> getAll() {
        return ResponseEntity.ok(driverService.getAll());
    }

    @GetMapping("/available")
    public ResponseEntity<List<DriverResponse>> getAvailable() {
        return ResponseEntity.ok(driverService.getAvailable());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.getById(id));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<DriverResponse> setAvailability(
            @PathVariable Long id,
            @RequestParam boolean available) {
        return ResponseEntity.ok(driverService.setAvailability(id, available));
    }
}
