package org.example.notificationservice.service;

import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.kafka.PasswordResetRequestedEvent;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "fromAddress", "noreply@clouddelivery.local");
    }

    private Notification buildNotification(String id, boolean read) {
        return Notification.builder()
                .id(id)
                .email("user@test.com")
                .type(NotificationType.PASSWORD_RESET)
                .message("A password reset code has been sent to your email address.")
                .createdAt(Instant.now())
                .read(read)
                .build();
    }

    @Test
    void handlePasswordReset_shouldSendEmailAndPersistNotification() {
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent();
        event.setEmail("user@test.com");
        event.setCode("123456");
        event.setRequestedAt(Instant.now().toString());

        when(notificationRepository.save(any())).thenReturn(buildNotification("n1", false));

        notificationService.handlePasswordReset(event);

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().getTo()).contains("user@test.com");
        assertThat(mailCaptor.getValue().getFrom()).isEqualTo("noreply@clouddelivery.local");
        assertThat(mailCaptor.getValue().getText()).contains("123456");

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().getType()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(notifCaptor.getValue().isRead()).isFalse();
    }

    @Test
    void getNotificationsForUser_shouldReturnListInDescendingOrder() {
        List<Notification> notifications = List.of(
                buildNotification("n1", false),
                buildNotification("n2", true)
        );
        when(notificationRepository.findByEmailOrderByCreatedAtDesc("user@test.com"))
                .thenReturn(notifications);

        List<NotificationResponse> result = notificationService.getNotificationsForUser("user@test.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("n1");
    }

    @Test
    void markAsRead_shouldSetReadFlagAndSave() {
        Notification notification = buildNotification("n1", false);
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));

        notificationService.markAsRead("n1");

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_unknownId_shouldThrowNoSuchElement() {
        when(notificationRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("unknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Notification not found");
    }
}
