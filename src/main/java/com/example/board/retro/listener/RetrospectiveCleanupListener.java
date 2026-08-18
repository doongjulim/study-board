package com.example.board.retro.listener;

import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.retro.service.RetrospectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 회원의 회고를 지운다.
 *
 * <p>회고는 게시글과 달리 남에게 보이지 않는 개인 기록이라, 익명으로 남길 이유가 없다.
 * 다른 정리와 서로 참조가 없으므로 순서는 뒤쪽이면 충분하다.</p>
 */
@Component
@RequiredArgsConstructor
public class RetrospectiveCleanupListener {

    private final RetrospectiveService retrospectiveService;

    @EventListener
    @Order(4)
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        retrospectiveService.deleteAllOf(event.memberId());
    }
}
