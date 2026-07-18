package org.example.deliveryservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deliveryservice.dto.AssignDriverRequest;
import org.example.deliveryservice.dto.DeliveryResponse;
import org.example.deliveryservice.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.getById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(deliveryService.getByOrderId(orderId));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<DeliveryResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.ok(deliveryService.assignDriver(id, request.driverId()));
    }

    @PatchMapping("/{id}/pickup")
    public ResponseEntity<DeliveryResponse> pickUp(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.markPickedUp(id));
    }

    @PatchMapping("/{id}/on-road")
    public ResponseEntity<DeliveryResponse> onRoad(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.markOnRoad(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<DeliveryResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.markDelivered(id));
    }

    @PatchMapping("/{id}/fail")
    public ResponseEntity<DeliveryResponse> fail(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.markFailed(id));
    }
}
