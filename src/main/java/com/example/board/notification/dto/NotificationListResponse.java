package com.example.board.notification.dto;

import java.util.List;

public record NotificationListResponse(long unreadCount, List<NotificationResponse> notifications) {
}
