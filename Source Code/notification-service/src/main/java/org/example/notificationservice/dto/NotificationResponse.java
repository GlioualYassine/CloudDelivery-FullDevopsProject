package org.example.notificationservice.dto;

import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String email,
        NotificationType type,
        String message,
        Instant createdAt,
        boolean read
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEmail(),
                notification.getType(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead()
        );
    }
}
