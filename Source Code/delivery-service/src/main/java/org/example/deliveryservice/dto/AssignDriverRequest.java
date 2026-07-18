package org.example.deliveryservice.dto;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(@NotNull Long driverId) {}
