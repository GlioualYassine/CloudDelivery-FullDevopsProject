package org.example.notificationservice.repository;

import org.example.notificationservice.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByEmailOrderByCreatedAtDesc(String email);
}
