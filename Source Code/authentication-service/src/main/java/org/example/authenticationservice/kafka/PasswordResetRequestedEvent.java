package org.example.authenticationservice.kafka;

// package org.example.authenticationservice.kafka;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PasswordResetRequestedEvent {
    private String email;
    private String code;          // le code envoyé par email
    private String requestedAt;
}
