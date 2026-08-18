package com.example.board.post.listener;

import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 회원이 누른 좋아요를 지운다.
 *
 * <p>글과 댓글은 스레드에 구멍이 생기지 않도록 익명으로 남기지만,
 * 좋아요는 남에게 보이지 않는 개인 기록이라 남길 이유가 없다.</p>
 *
 * <p>좋아요 수 카운터는 건드리지 않는다. 남의 글에 붙은 숫자가 어느 날 갑자기 줄어드는 편이
 * 지운 사람 몫이 남아 있는 것보다 더 이상하게 읽히기 때문이다.</p>
 */
@Component
@RequiredArgsConstructor
public class PostLikeCleanupListener {

    private final PostService postService;

    @EventListener
    @Order(5)
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        postService.deleteLikesOf(event.memberId());
    }
}
