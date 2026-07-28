package com.example.board.notification.repository;

import com.example.board.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByOrderByIdDesc();

    List<Notification> findByReadFlagFalse();

    long countByReadFlagFalse();
}
