package org.example.deliveryservice.service;

import org.example.deliveryservice.dto.DriverRequest;
import org.example.deliveryservice.dto.DriverResponse;
import org.example.deliveryservice.model.Driver;
import org.example.deliveryservice.repository.DriverRepository;
import org.example.deliveryservice.service.impl.DriverServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverServiceImpl driverService;

    private Driver buildDriver(Long id, boolean available) {
        return Driver.builder()
                .id(id)
                .name("John Driver")
                .email("driver@test.com")
                .phone("+33600000000")
                .available(available)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void register_shouldCreateDriverWithAvailableTrue() {
        DriverRequest request = new DriverRequest("John Driver", "driver@test.com", "+33600000000");
        Driver saved = buildDriver(1L, true);
        when(driverRepository.save(any(Driver.class))).thenReturn(saved);

        DriverResponse response = driverService.register(request);

        assertThat(response.name()).isEqualTo("John Driver");
        assertThat(response.available()).isTrue();
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    void getById_shouldReturnDriverResponse() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(buildDriver(1L, true)));

        DriverResponse response = driverService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.available()).isTrue();
    }

    @Test
    void getById_unknownId_shouldThrowNotFound() {
        when(driverRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAvailable_shouldReturnOnlyAvailableDrivers() {
        when(driverRepository.findByAvailableTrue())
                .thenReturn(List.of(buildDriver(1L, true), buildDriver(2L, true)));

        List<DriverResponse> result = driverService.getAvailable();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(DriverResponse::available);
    }

    @Test
    void setAvailability_shouldUpdateDriverStatus() {
        Driver driver = buildDriver(1L, true);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any())).thenReturn(driver);

        DriverResponse response = driverService.setAvailability(1L, false);

        assertThat(driver.isAvailable()).isFalse();
        verify(driverRepository).save(driver);
    }
}
