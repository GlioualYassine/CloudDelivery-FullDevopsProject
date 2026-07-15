package org.example.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{email}")
    public ResponseEntity<List<NotificationResponse>> getByUser(@PathVariable String email) {
        return ResponseEntity.ok(notificationService.getNotificationsForUser(email));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        notificationService.markAsRead(id);
        return ResponseEntity.noContent().build();
    }
}
