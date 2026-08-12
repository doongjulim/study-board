package com.example.board.session.listener;

import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.session.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 학습 기록을 정리한다.
 *
 * <p>학습 기록은 계획을 참조하므로 계획보다 <b>먼저</b> 지운다.
 * (계획을 먼저 지우면 plan_id 만 null 이 되고 기록은 남아 고아 데이터가 된다)</p>
 */
@Component
@RequiredArgsConstructor
public class StudySessionCleanupListener {

    private final StudySessionRepository sessionRepository;

    @EventListener
    @Order(1)
    @Transactional
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        sessionRepository.deleteByOwner_Id(event.memberId());
    }
}
