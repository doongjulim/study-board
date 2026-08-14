package com.example.board.notification.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.dto.NotificationResponse;
import com.example.board.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String EVENT_NAME = "notification";

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final SseEmitterRegistry emitterRegistry;

    /**
     * 특정 회원에게 알림을 저장하고, 접속 중이면 실시간 전송한다.
     * 그 종류를 꺼 둔 회원에게는 아무것도 남기지 않는다 (읽지 않은 개수도 늘지 않는다).
     */
    @Transactional
    public void notify(Long recipientId, NotificationType type, String message, String url) {
        memberRepository.findById(recipientId)
                .filter(recipient -> !recipient.isWithdrawn())
                .filter(recipient -> type.allowedBy(recipient.getNotificationPreference()))
                .ifPresent(recipient -> send(recipient, message, url));
    }

    /**
     * 발신자를 제외한 모든 회원에게 플랜 공유 소식을 뿌린다 (전체 공개).
     * 공유 알림을 꺼 둔 회원과 탈퇴한 회원은 조회 단계에서 걸러, 사람 수만큼 설정을 되묻지 않는다.
     */
    @Transactional
    public void notifyAllExcept(Long exceptMemberId, String message, String url) {
        fanout(memberRepository.findIdsAllowingPlanSharedNotification(), exceptMemberId, message, url);
    }

    /**
     * 지정한 사람들에게만 뿌린다 (그룹 공개).
     * 대상이 비어 있으면 조회 자체를 건너뛴다 - 빈 in () 은 쿼리 오류이고, 보낼 곳도 없다.
     */
    @Transactional
    public void notifyMembersExcept(Collection<Long> candidateIds, Long exceptMemberId,
                                    String message, String url) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return;
        }
        fanout(memberRepository.findIdsAllowingPlanSharedNotificationIn(candidateIds),
                exceptMemberId, message, url);
    }

    /** 공유 알림 발송 대상은 이미 설정으로 걸러진 id 목록이다 - 여기서는 발신자만 뺀다 */
    private void fanout(List<Long> recipientIds, Long exceptMemberId, String message, String url) {
        recipientIds.stream()
                .filter(memberId -> !memberId.equals(exceptMemberId))
                .forEach(memberId -> send(memberRepository.getReferenceById(memberId), message, url));
    }

    private void send(Member recipient, String message, String url) {
        Notification saved = notificationRepository.save(new Notification(recipient, message, url));
        emitterRegistry.send(recipient.getId(), EVENT_NAME, String.valueOf(saved.getId()),
                NotificationResponse.from(saved));
    }

    /**
     * SSE 구독. 재연결이면 브라우저가 마지막으로 받은 이벤트 id 를 보내주므로,
     * 끊겨 있는 동안 쌓인 알림을 이어서 전송한다.
     */
    public SseEmitter subscribe(Long memberId, String lastEventId) {
        SseEmitter emitter = emitterRegistry.add(memberId);
        replayMissed(memberId, lastEventId, emitter);
        return emitter;
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

    private void replayMissed(Long memberId, String lastEventId, SseEmitter emitter) {
        parseEventId(lastEventId).ifPresent(lastId ->
                notificationRepository.findByRecipient_IdAndIdGreaterThanOrderByIdAsc(memberId, lastId)
                        .forEach(missed -> emitterRegistry.sendTo(emitter, EVENT_NAME,
                                String.valueOf(missed.getId()), NotificationResponse.from(missed))));
    }

    /** 클라이언트가 보낸 값이라 신뢰할 수 없으므로 숫자가 아니면 조용히 무시한다 */
    private Optional<Long> parseEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(lastEventId.trim()));
        } catch (NumberFormatException e) {
            log.debug("잘못된 Last-Event-ID 무시: {}", lastEventId);
            return Optional.empty();
        }
    }
}
