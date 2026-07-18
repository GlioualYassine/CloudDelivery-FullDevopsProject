package org.example.deliveryservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.delivery-status-topic}")
    private String deliveryStatusTopic;

    public void publishStatusUpdate(DeliveryStatusUpdatedEvent event) {
        kafkaTemplate.send(deliveryStatusTopic, event.getOrderId().toString(), event);
        log.info("Published DeliveryStatusUpdatedEvent: orderId={} status={}", event.getOrderId(), event.getStatus());
    }
}
