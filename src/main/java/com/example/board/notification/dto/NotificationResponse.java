package com.example.board.notification.dto;

import com.example.board.notification.domain.Notification;

import java.time.format.DateTimeFormatter;

public record NotificationResponse(Long id, String message, String url, boolean read, String createdAt) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.getUrl(),
                notification.isReadFlag(),
                notification.getCreatedAt() != null ? notification.getCreatedAt().format(FORMATTER) : ""
        );
    }
}
