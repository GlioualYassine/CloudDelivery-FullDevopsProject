package org.example.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.order-confirmed-topic}")
    private String orderConfirmedTopic;

    @Value("${app.kafka.order-cancelled-topic}")
    private String orderCancelledTopic;

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        kafkaTemplate.send(orderConfirmedTopic, event.getOrderId().toString(), event);
        log.info("Published OrderConfirmedEvent for order id={}", event.getOrderId());
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(orderCancelledTopic, event.getOrderId().toString(), event);
        log.info("Published OrderCancelledEvent for order id={}", event.getOrderId());
    }
}
