package org.example.deliveryservice.dto;

import org.example.deliveryservice.model.Driver;

import java.time.Instant;

public record DriverResponse(
        Long id,
        String name,
        String email,
        String phone,
        boolean available,
        Instant createdAt
) {
    public static DriverResponse from(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getName(),
                driver.getEmail(),
                driver.getPhone(),
                driver.isAvailable(),
                driver.getCreatedAt()
        );
    }
}
