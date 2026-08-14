package com.example.board.group.listener;

import com.example.board.group.service.StudyGroupService;
import com.example.board.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 회원의 그룹 소속을 정리한다.
 *
 * <p>단순히 소속 행을 지우면 그룹장이 탈퇴한 그룹이 주인 없는 채로 남는다.
 * 나가기와 같은 규칙(가장 오래된 멤버 승계, 혼자면 그룹 삭제)을 그대로 쓴다.
 * 세션(1)·플랜(2) 정리와 서로 참조가 없으므로 순서는 그 뒤면 충분하다.</p>
 */
@Component
@RequiredArgsConstructor
public class GroupCleanupListener {

    private final StudyGroupService studyGroupService;

    @EventListener
    @Order(3)
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        studyGroupService.leaveAll(event.memberId());
    }
}
