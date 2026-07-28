package com.example.board.notification.service;

import com.example.board.notification.domain.Notification;
import com.example.board.notification.dto.NotificationResponse;
import com.example.board.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistry emitterRegistry;

    /** 알림을 저장하고 접속 중인 모든 클라이언트에 실시간 전송한다 */
    @Transactional
    public void notify(String message, String url) {
        Notification saved = notificationRepository.save(new Notification(message, url));
        emitterRegistry.broadcast("notification", NotificationResponse.from(saved));
    }

    public SseEmitter subscribe() {
        return emitterRegistry.add();
    }

    public List<Notification> findRecent() {
        return notificationRepository.findTop10ByOrderByIdDesc();
    }

    public long countUnread() {
        return notificationRepository.countByReadFlagFalse();
    }

    @Transactional
    public void markAllAsRead() {
        notificationRepository.findByReadFlagFalse().forEach(Notification::markRead);
    }
}
