package org.example.deliveryservice.service;

import org.example.deliveryservice.dto.DeliveryResponse;

public interface DeliveryService {

    void createDelivery(Long orderId, String clientEmail);

    DeliveryResponse getById(Long id);

    DeliveryResponse getByOrderId(Long orderId);

    DeliveryResponse assignDriver(Long deliveryId, Long driverId);

    DeliveryResponse markPickedUp(Long deliveryId);

    DeliveryResponse markOnRoad(Long deliveryId);

    DeliveryResponse markDelivered(Long deliveryId);

    DeliveryResponse markFailed(Long deliveryId);
}
