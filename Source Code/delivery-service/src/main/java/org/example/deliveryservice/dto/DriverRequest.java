package org.example.deliveryservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DriverRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone
) {}
