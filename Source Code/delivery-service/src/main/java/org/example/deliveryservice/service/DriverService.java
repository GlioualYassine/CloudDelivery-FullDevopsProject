package org.example.deliveryservice.service;

import org.example.deliveryservice.dto.DriverRequest;
import org.example.deliveryservice.dto.DriverResponse;

import java.util.List;

public interface DriverService {

    DriverResponse register(DriverRequest request);

    DriverResponse getById(Long id);

    List<DriverResponse> getAll();

    List<DriverResponse> getAvailable();

    DriverResponse setAvailability(Long id, boolean available);
}
