package org.example.authenticationservice.kafka;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetProducer {

    private final KafkaTemplate<String, PasswordResetRequestedEvent> kafkaTemplate;

    @Value("${app.kafka.password-reset-topic}")
    private String topic;

    public void sendPasswordResetRequested(String email, String code) {
        PasswordResetRequestedEvent event = PasswordResetRequestedEvent.builder()
                .email(email)
                .code(code)
                .requestedAt(Instant.now().toString())
                .build();

        kafkaTemplate.send(topic, email, event);
    }
}
