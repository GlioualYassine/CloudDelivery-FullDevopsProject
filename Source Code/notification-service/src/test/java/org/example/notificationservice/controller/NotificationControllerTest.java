package org.example.notificationservice.controller;

import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
    }

    private NotificationResponse buildResponse(String id, boolean read) {
        return new NotificationResponse(id, "user@test.com", NotificationType.PASSWORD_RESET,
                "Reset code sent.", Instant.now(), read);
    }

    @Test
    void getByUser_shouldReturn200WithNotificationList() throws Exception {
        when(notificationService.getNotificationsForUser("user@test.com"))
                .thenReturn(List.of(buildResponse("n1", false), buildResponse("n2", true)));

        mockMvc.perform(get("/api/v1/notifications/user/user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("n1"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void getByUser_noNotifications_shouldReturn200WithEmptyList() throws Exception {
        when(notificationService.getNotificationsForUser("empty@test.com")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/user/empty@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void markAsRead_existingId_shouldReturn204() throws Exception {
        doNothing().when(notificationService).markAsRead("n1");

        mockMvc.perform(patch("/api/v1/notifications/n1/read"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAsRead("n1");
    }
}
