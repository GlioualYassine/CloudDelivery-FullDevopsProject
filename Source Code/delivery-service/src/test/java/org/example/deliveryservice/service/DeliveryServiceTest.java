package org.example.deliveryservice.service;

import org.example.deliveryservice.dto.DeliveryResponse;
import org.example.deliveryservice.kafka.DeliveryEventProducer;
import org.example.deliveryservice.kafka.DeliveryStatusUpdatedEvent;
import org.example.deliveryservice.model.Delivery;
import org.example.deliveryservice.model.DeliveryStatus;
import org.example.deliveryservice.model.Driver;
import org.example.deliveryservice.repository.DeliveryRepository;
import org.example.deliveryservice.repository.DriverRepository;
import org.example.deliveryservice.service.impl.DeliveryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private DeliveryEventProducer eventProducer;

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private Delivery buildDelivery(Long id, DeliveryStatus status) {
        return Delivery.builder()
                .id(id)
                .orderId(10L)
                .clientEmail("client@test.com")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Driver buildDriver(Long id, boolean available) {
        return Driver.builder()
                .id(id)
                .name("John Driver")
                .email("driver@test.com")
                .available(available)
                .build();
    }

    @Test
    void createDelivery_noExistingDelivery_shouldPersist() {
        when(deliveryRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenReturn(buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT));

        deliveryService.createDelivery(10L, "client@test.com");

        verify(deliveryRepository).save(any(Delivery.class));
    }

    @Test
    void createDelivery_alreadyExists_shouldSkipPersist() {
        when(deliveryRepository.findByOrderId(10L))
                .thenReturn(Optional.of(buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT)));

        deliveryService.createDelivery(10L, "client@test.com");

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void getById_shouldReturnDeliveryResponse() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT)));

        DeliveryResponse response = deliveryService.getById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(DeliveryStatus.PENDING_ASSIGNMENT);
    }

    @Test
    void getById_unknownId_shouldThrowNotFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getByOrderId_unknownOrderId_shouldThrowNotFound() {
        when(deliveryRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getByOrderId(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void assignDriver_shouldAssignDriverAndMarkUnavailable() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT);
        Driver driver = buildDriver(5L, true);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(driverRepository.findById(5L)).thenReturn(Optional.of(driver));
        when(deliveryRepository.save(any())).thenReturn(delivery);

        deliveryService.assignDriver(1L, 5L);

        assertThat(driver.isAvailable()).isFalse();
        verify(driverRepository).save(driver);
        verify(eventProducer).publishStatusUpdate(any(DeliveryStatusUpdatedEvent.class));
    }

    @Test
    void assignDriver_wrongStatus_shouldThrowConflict() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.ASSIGNED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.assignDriver(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void assignDriver_driverNotFound_shouldThrowNotFound() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(driverRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.assignDriver(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void assignDriver_driverNotAvailable_shouldThrowConflict() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT);
        Driver driver = buildDriver(5L, false);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(driverRepository.findById(5L)).thenReturn(Optional.of(driver));

        assertThatThrownBy(() -> deliveryService.assignDriver(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void markPickedUp_fromAssigned_shouldTransitionAndPublishEvent() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.ASSIGNED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenReturn(delivery);

        deliveryService.markPickedUp(1L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        verify(eventProducer).publishStatusUpdate(any());
    }

    @Test
    void markPickedUp_wrongStatus_shouldThrowConflict() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.PENDING_ASSIGNMENT);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.markPickedUp(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void markOnRoad_fromPickedUp_shouldTransition() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.PICKED_UP);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenReturn(delivery);

        deliveryService.markOnRoad(1L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ON_ROAD);
    }

    @Test
    void markDelivered_fromOnRoad_shouldSetDeliveredAtAndReleaseDriver() {
        Driver driver = buildDriver(5L, false);
        Delivery delivery = buildDelivery(1L, DeliveryStatus.ON_ROAD);
        delivery.setDriver(driver);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenReturn(delivery);

        deliveryService.markDelivered(1L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
        assertThat(driver.isAvailable()).isTrue();
        verify(driverRepository).save(driver);
    }

    @Test
    void markFailed_shouldReleaseDriverAndPublishEvent() {
        Driver driver = buildDriver(5L, false);
        Delivery delivery = buildDelivery(1L, DeliveryStatus.ON_ROAD);
        delivery.setDriver(driver);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenReturn(delivery);

        deliveryService.markFailed(1L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(driver.isAvailable()).isTrue();
        verify(eventProducer).publishStatusUpdate(any());
    }

    @Test
    void markFailed_fromTerminalState_shouldThrowConflict() {
        Delivery delivery = buildDelivery(1L, DeliveryStatus.DELIVERED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.markFailed(1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
