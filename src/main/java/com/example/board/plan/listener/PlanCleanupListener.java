package com.example.board.plan.listener;

import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 계획을 정리한다 (계획에 달린 댓글은 DB 의 on delete cascade 로 함께 지워진다).
 * 학습 기록이 계획을 참조하므로 그쪽 정리가 끝난 뒤에 실행한다.
 */
@Component
@RequiredArgsConstructor
public class PlanCleanupListener {

    private final PlanRepository planRepository;

    @EventListener
    @Order(2)
    @Transactional
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        planRepository.deleteByAuthor_Id(event.memberId());
    }
}
