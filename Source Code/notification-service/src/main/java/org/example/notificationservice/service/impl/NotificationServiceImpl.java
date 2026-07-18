package org.example.notificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.kafka.PasswordResetRequestedEvent;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Override
    public void handlePasswordReset(PasswordResetRequestedEvent event) {
        sendPasswordResetEmail(event.getEmail(), event.getCode());
        persist(event.getEmail(), NotificationType.PASSWORD_RESET,
                "A password reset code has been sent to your email address.");
    }

    @Override
    public List<NotificationResponse> getNotificationsForUser(String email) {
        return notificationRepository.findByEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public void markAsRead(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private void sendPasswordResetEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("CloudDelivery - Password Reset Request");
        message.setText(buildPasswordResetBody(code));
        mailSender.send(message);
        log.info("Password reset email dispatched to {}", to);
    }

    private String buildPasswordResetBody(String code) {
        return String.format(
                "You have requested a password reset for your CloudDelivery account.%n%n"
                + "Reset code: %s%n%n"
                + "This code expires in 15 minutes. If you did not request this, disregard this email.",
                code
        );
    }

    private void persist(String email, NotificationType type, String message) {
        notificationRepository.save(
                Notification.builder()
                        .email(email)
                        .type(type)
                        .message(message)
                        .createdAt(Instant.now())
                        .read(false)
                        .build()
        );
    }
}
