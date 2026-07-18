package org.example.deliveryservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deliveryservice.service.DeliveryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    private final DeliveryService deliveryService;

    @KafkaListener(
            topics = "${app.kafka.order-confirmed-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received OrderConfirmedEvent: orderId={}", event.getOrderId());
        deliveryService.createDelivery(event.getOrderId(), event.getClientEmail());
    }
}
