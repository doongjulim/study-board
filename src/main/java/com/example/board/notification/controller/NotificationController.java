package com.example.board.notification.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.notification.dto.NotificationListResponse;
import com.example.board.notification.dto.NotificationResponse;
import com.example.board.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * SSE 구독 - 브라우저의 EventSource 가 연결한다 (본인 알림만 수신).
     * 재연결이면 EventSource 가 Last-Event-ID 를 보내며, 그동안 놓친 알림을 이어서 받는다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal MemberPrincipal principal,
                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return notificationService.subscribe(principal.id(), lastEventId);
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

    /**
     * 알림 하나만 읽음 처리.
     *
     * <p>패널을 여는 것만으로 전부 읽음이 되면 "이건 나중에" 를 남길 수 없다.
     * 읽었다는 표시는 사용자가 그 알림을 골랐을 때 붙는다.</p>
     */
    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAsRead(principal.id(), id);
    }

    /** 알림 하나 삭제 */
    @PostMapping("/{id}/delete")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.delete(principal.id(), id);
    }

    /** 내 알림 모두 삭제 */
    @PostMapping("/delete-all")
    public void deleteAll(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.deleteAll(principal.id());
    }
}
