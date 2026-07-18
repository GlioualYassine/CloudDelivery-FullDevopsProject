package org.example.deliveryservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.deliveryservice.dto.DriverRequest;
import org.example.deliveryservice.dto.DriverResponse;
import org.example.deliveryservice.model.Driver;
import org.example.deliveryservice.repository.DriverRepository;
import org.example.deliveryservice.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Override
    @Transactional
    public DriverResponse register(DriverRequest request) {
        Driver driver = Driver.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .available(true)
                .build();
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Override
    public DriverResponse getById(Long id) {
        return DriverResponse.from(findOrThrow(id));
    }

    @Override
    public List<DriverResponse> getAll() {
        return driverRepository.findAll().stream()
                .map(DriverResponse::from)
                .toList();
    }

    @Override
    public List<DriverResponse> getAvailable() {
        return driverRepository.findByAvailableTrue().stream()
                .map(DriverResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public DriverResponse setAvailability(Long id, boolean available) {
        Driver driver = findOrThrow(id);
        driver.setAvailable(available);
        return DriverResponse.from(driverRepository.save(driver));
    }

    private Driver findOrThrow(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found: " + id));
    }
}
