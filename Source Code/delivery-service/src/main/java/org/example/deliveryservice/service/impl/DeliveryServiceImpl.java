package org.example.deliveryservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deliveryservice.dto.DeliveryResponse;
import org.example.deliveryservice.kafka.DeliveryEventProducer;
import org.example.deliveryservice.kafka.DeliveryStatusUpdatedEvent;
import org.example.deliveryservice.model.Delivery;
import org.example.deliveryservice.model.DeliveryStatus;
import org.example.deliveryservice.model.Driver;
import org.example.deliveryservice.repository.DeliveryRepository;
import org.example.deliveryservice.repository.DriverRepository;
import org.example.deliveryservice.service.DeliveryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final DeliveryEventProducer eventProducer;

    @Override
    @Transactional
    public void createDelivery(Long orderId, String clientEmail) {
        if (deliveryRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Delivery already exists for orderId={}, skipping", orderId);
            return;
        }
        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .clientEmail(clientEmail)
                .status(DeliveryStatus.PENDING_ASSIGNMENT)
                .build();
        deliveryRepository.save(delivery);
        log.info("Created delivery for orderId={}", orderId);
    }

    @Override
    public DeliveryResponse getById(Long id) {
        return DeliveryResponse.from(findOrThrow(id));
    }

    @Override
    public DeliveryResponse getByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No delivery found for orderId: " + orderId));
        return DeliveryResponse.from(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse assignDriver(Long deliveryId, Long driverId) {
        Delivery delivery = findOrThrow(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.PENDING_ASSIGNMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Delivery " + deliveryId + " is not in PENDING_ASSIGNMENT state");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found: " + driverId));
        if (!driver.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver " + driverId + " is not available");
        }
        driver.setAvailable(false);
        driverRepository.save(driver);

        delivery.setDriver(driver);
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        Delivery saved = deliveryRepository.save(delivery);

        publishEvent(saved);
        return DeliveryResponse.from(saved);
    }

    @Override
    @Transactional
    public DeliveryResponse markPickedUp(Long deliveryId) {
        return transition(deliveryId, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP);
    }

    @Override
    @Transactional
    public DeliveryResponse markOnRoad(Long deliveryId) {
        return transition(deliveryId, DeliveryStatus.PICKED_UP, DeliveryStatus.ON_ROAD);
    }

    @Override
    @Transactional
    public DeliveryResponse markDelivered(Long deliveryId) {
        Delivery delivery = findOrThrow(deliveryId);
        requireStatus(delivery, DeliveryStatus.ON_ROAD);
        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(Instant.now());
        releaseDriver(delivery);
        Delivery saved = deliveryRepository.save(delivery);
        publishEvent(saved);
        return DeliveryResponse.from(saved);
    }

    @Override
    @Transactional
    public DeliveryResponse markFailed(Long deliveryId) {
        Delivery delivery = findOrThrow(deliveryId);
        if (delivery.getStatus() == DeliveryStatus.DELIVERED || delivery.getStatus() == DeliveryStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Delivery " + deliveryId + " is already in terminal state " + delivery.getStatus());
        }
        delivery.setStatus(DeliveryStatus.FAILED);
        releaseDriver(delivery);
        Delivery saved = deliveryRepository.save(delivery);
        publishEvent(saved);
        return DeliveryResponse.from(saved);
    }

    private DeliveryResponse transition(Long deliveryId, DeliveryStatus from, DeliveryStatus to) {
        Delivery delivery = findOrThrow(deliveryId);
        requireStatus(delivery, from);
        delivery.setStatus(to);
        Delivery saved = deliveryRepository.save(delivery);
        publishEvent(saved);
        return DeliveryResponse.from(saved);
    }

    private void requireStatus(Delivery delivery, DeliveryStatus required) {
        if (delivery.getStatus() != required) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Delivery " + delivery.getId() + " must be in " + required + " state, found " + delivery.getStatus());
        }
    }

    private void releaseDriver(Delivery delivery) {
        if (delivery.getDriver() != null) {
            delivery.getDriver().setAvailable(true);
            driverRepository.save(delivery.getDriver());
        }
    }

    private void publishEvent(Delivery delivery) {
        String driverName = delivery.getDriver() != null ? delivery.getDriver().getName() : null;
        eventProducer.publishStatusUpdate(DeliveryStatusUpdatedEvent.builder()
                .deliveryId(delivery.getId())
                .orderId(delivery.getOrderId())
                .clientEmail(delivery.getClientEmail())
                .driverName(driverName)
                .status(delivery.getStatus())
                .updatedAt(Instant.now().toString())
                .build());
    }

    private Delivery findOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found: " + id));
    }
}
