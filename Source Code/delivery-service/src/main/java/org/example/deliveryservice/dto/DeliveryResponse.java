package org.example.deliveryservice.dto;

import org.example.deliveryservice.model.Delivery;
import org.example.deliveryservice.model.DeliveryStatus;

import java.time.Instant;

public record DeliveryResponse(
        Long id,
        Long orderId,
        String clientEmail,
        DriverResponse driver,
        DeliveryStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        DriverResponse driver = delivery.getDriver() != null
                ? DriverResponse.from(delivery.getDriver())
                : null;
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getClientEmail(),
                driver,
                delivery.getStatus(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getDeliveredAt()
        );
    }
}
