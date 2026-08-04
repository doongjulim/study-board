package com.example.board.notification.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.notification.dto.NotificationListResponse;
import com.example.board.notification.dto.NotificationResponse;
import com.example.board.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /** SSE 구독 - 브라우저의 EventSource 가 연결한다 (본인 알림만 수신) */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal MemberPrincipal principal) {
        return notificationService.subscribe(principal.id());
    }

    /** 내 알림 목록 + 읽지 않은 개수 */
    @GetMapping
    public NotificationListResponse list(@AuthenticationPrincipal MemberPrincipal principal) {
        List<NotificationResponse> notifications = notificationService.findRecent(principal.id()).stream()
                .map(NotificationResponse::from)
                .toList();
        return new NotificationListResponse(notificationService.countUnread(principal.id()), notifications);
    }

    /** 내 알림 모두 읽음 처리 */
    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAllAsRead(principal.id());
    }
}
