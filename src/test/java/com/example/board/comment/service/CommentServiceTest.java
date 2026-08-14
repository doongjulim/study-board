package com.example.board.comment.service;

import com.example.board.comment.domain.Comment;
import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.comment.repository.CommentRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.ShareScope;
import com.example.board.plan.service.PlanService;
import com.example.board.post.domain.Post;
import com.example.board.post.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final long POST_AUTHOR_ID = 10L;
    private static final long COMMENTER_ID = 20L;

    @Mock CommentRepository commentRepository;
    @Mock PostRepository postRepository;
    @Mock PlanService planService;
    @Mock MemberRepository memberRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CommentService commentService;

    private Member member(long id, String nickname) {
        Member member = new Member("login" + id, "encoded-password", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Post post() {
        return new Post("면접 후기", "내용", member(POST_AUTHOR_ID, "글쓴이"));
    }

    private Plan sharedPlan() {
        Plan plan = new Plan("모의면접", null, member(POST_AUTHOR_ID, "글쓴이"), PlanCategory.INTERVIEW,
                LocalDate.of(2026, 7, 30), null, null);
        plan.changeShareScope(ShareScope.PUBLIC);
        return plan;
    }

    @Test
    @DisplayName("게시글 댓글 작성 시 저장하고 글 작성자에게 알림 이벤트를 발행한다")
    void addToPost_publishesEvent() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));
        given(memberRepository.getReferenceById(COMMENTER_ID)).willReturn(member(COMMENTER_ID, "댓글러"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.addToPost(1L, COMMENTER_ID, "좋은 글이네요");

        then(commentRepository).should().save(any(Comment.class));
        ArgumentCaptor<CommentAddedEvent> captor = ArgumentCaptor.forClass(CommentAddedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(POST_AUTHOR_ID);
        assertThat(captor.getValue().commenterNickname()).isEqualTo("댓글러");
        assertThat(captor.getValue().url()).isEqualTo("/posts/1");
    }

    @Test
    @DisplayName("내 글에 내가 단 댓글은 알림 이벤트를 발행하지 않는다")
    void addToPost_selfComment_noEvent() {
        given(postRepository.findById(1L)).willReturn(Optional.of(post()));
        given(memberRepository.getReferenceById(POST_AUTHOR_ID)).willReturn(member(POST_AUTHOR_ID, "글쓴이"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.addToPost(1L, POST_AUTHOR_ID, "셀프 댓글");

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공유된 플랜에 댓글을 달면 플랜 작성자에게 알림 이벤트를 발행한다")
    void addToPlan_publishesEvent() {
        Plan shared = sharedPlan();
        given(planService.findById(2L)).willReturn(shared);
        given(planService.canView(shared, COMMENTER_ID)).willReturn(true);
        given(memberRepository.getReferenceById(COMMENTER_ID)).willReturn(member(COMMENTER_ID, "댓글러"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.addToPlan(2L, COMMENTER_ID, "화이팅!");

        ArgumentCaptor<CommentAddedEvent> captor = ArgumentCaptor.forClass(CommentAddedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(POST_AUTHOR_ID);
        assertThat(captor.getValue().url()).isEqualTo("/plans/shared/2");
    }

    @Test
    @DisplayName("볼 수 없는 플랜에는 댓글을 달 수 없다 (그룹 공개도 같은 그룹이 아니면 거절)")
    void addToPlan_notVisible_denied() {
        Plan privatePlan = new Plan("비공개 플랜", null, member(POST_AUTHOR_ID, "글쓴이"), PlanCategory.ETC,
                LocalDate.of(2026, 7, 30), null, null);
        given(planService.findById(2L)).willReturn(privatePlan);
        given(planService.canView(privatePlan, COMMENTER_ID)).willReturn(false);

        assertThatThrownBy(() -> commentService.addToPlan(2L, COMMENTER_ID, "몰래 댓글"))
                .isInstanceOf(AccessDeniedException.class);
        then(commentRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("본인 댓글은 삭제할 수 있다")
    void delete_owner() {
        Comment comment = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "내 댓글");
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        commentService.delete(5L, COMMENTER_ID);

        then(commentRepository).should().delete(comment);
    }

    @Test
    @DisplayName("타인의 댓글을 삭제하려 하면 AccessDeniedException 이 발생한다")
    void delete_notOwner_denied() {
        Comment comment = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "남의 댓글");
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(5L, 999L))
                .isInstanceOf(AccessDeniedException.class);
        then(commentRepository).should(never()).delete(any(Comment.class));
    }
}
