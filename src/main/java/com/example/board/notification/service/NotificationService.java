package com.example.board.notification.service;

import com.example.board.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;
    private final SseEmitterRegistry emitterRegistry;

    /** 특정 회원에게 알림을 저장하고, 접속 중이면 실시간 전송한다 */
    @Transactional
    public void notify(Long recipientId, String message, String url) {
        Notification saved = notificationRepository.save(
                new Notification(memberRepository.getReferenceById(recipientId), message, url));
        emitterRegistry.send(recipientId, "notification", NotificationResponse.from(saved));
    }

    /** 발신자를 제외한 모든 회원에게 알림을 뿌린다 (플랜 공유 등 공지형) */
    @Transactional
    public void notifyAllExcept(Long exceptMemberId, String message, String url) {
        memberRepository.findAllIds().stream()
                .filter(memberId -> !memberId.equals(exceptMemberId))
                .forEach(memberId -> notify(memberId, message, url));
    }

    public SseEmitter subscribe(Long memberId) {
        return emitterRegistry.add(memberId);
    }

    public List<Notification> findRecent(Long memberId) {
        return notificationRepository.findTop10ByRecipient_IdOrderByIdDesc(memberId);
    }

    public long countUnread(Long memberId) {
        return notificationRepository.countByRecipient_IdAndReadFlagFalse(memberId);
    }

    @Transactional
    public void markAllAsRead(Long memberId) {
        notificationRepository.findByRecipient_IdAndReadFlagFalse(memberId)
                .forEach(Notification::markRead);
    }
}
