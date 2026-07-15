package org.example.notificationservice.service;

import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.kafka.PasswordResetRequestedEvent;

import java.util.List;

public interface NotificationService {

    void handlePasswordReset(PasswordResetRequestedEvent event);

    List<NotificationResponse> getNotificationsForUser(String email);

    void markAsRead(String notificationId);
}
