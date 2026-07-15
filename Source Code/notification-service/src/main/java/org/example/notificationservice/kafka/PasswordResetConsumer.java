package org.example.notificationservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.password-reset-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("Received password reset event from Kafka: {}", event);
        notificationService.handlePasswordReset(event);
    }
}
