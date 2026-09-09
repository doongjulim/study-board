package com.example.board.comment.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.service.NotificationService;
import com.example.board.post.domain.Post;
import com.example.board.post.repository.PostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 답글 → 알림 경로를 <b>실제 DB 스키마 위에서</b> 걸어 본다.
 *
 * <p>이 테스트가 있어야 하는 이유: 서비스 단위 테스트는 리포지토리가 mock 이라 컬럼 길이를 모른다.
 * 그래서 "원댓글 전문을 알림 메시지에 넣는다" 는 코드가 통과했고, 500자 댓글에 답글을 다는 순간
 * 커밋에서 <code>Value too long for column MESSAGE</code> 가 나며 <b>답글까지 롤백됐다</b>.
 * 길이 규칙은 DB 가 가진 것이므로, 그 규칙을 지키는지는 DB 를 세워 두고 확인해야 한다.</p>
 */
@SpringBootTest
@Transactional
class CommentReplyNotificationIntegrationTest {

    @Autowired CommentService commentService;
    @Autowired NotificationService notificationService;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired EntityManager em;

    private Member rootAuthor;
    private Member replier;
    private Post post;

    @BeforeEach
    void setUp() {
        rootAuthor = memberRepository.save(new Member("rootauthor", "encoded-password", "원댓글러"));
        replier = memberRepository.save(new Member("replier", "encoded-password", "답글러"));
        post = postRepository.save(new Post("면접 후기", "본문", rootAuthor));
    }

    @Test
    @DisplayName("500자 원댓글에 답글을 달아도 답글이 저장된다")
    void replyToLongComment_isSaved() {
        Long rootId = commentService.addToPost(post.getId(), rootAuthor.getId(), "가".repeat(500));
        em.flush();

        Long replyId = commentService.reply(rootId, replier.getId(), "저도 궁금합니다");
        em.flush(); // 커밋 시점에 터지던 결함이라, 반드시 밀어내 본다

        assertThat(replyId).isNotNull();
        assertThat(commentService.findById(replyId).getContent()).isEqualTo("저도 궁금합니다");
    }

    @Test
    @DisplayName("그 답글의 알림도 함께 저장되고, 메시지가 컬럼 상한 안에 들어온다")
    void replyToLongComment_notificationFitsColumn() {
        Long rootId = commentService.addToPost(post.getId(), rootAuthor.getId(), "나".repeat(500));
        em.flush();

        commentService.reply(rootId, replier.getId(), "저도 궁금합니다");
        em.flush();

        List<Notification> notifications = notificationService.findRecent(rootAuthor.getId());
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.get(0).getMessage())
                .hasSizeLessThanOrEqualTo(Notification.MAX_MESSAGE_LENGTH)
                .contains("답글러");
    }
}
