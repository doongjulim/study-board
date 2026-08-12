package com.example.board.notification.listener;

import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원이 받은 알림을 정리한다.
 * 알림 변환을 담당하는 {@link NotificationEventListener} 와 책임이 달라 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class NotificationCleanupListener {

    private final NotificationRepository notificationRepository;

    @EventListener
    @Transactional
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        notificationRepository.deleteByRecipient_Id(event.memberId());
    }
}
