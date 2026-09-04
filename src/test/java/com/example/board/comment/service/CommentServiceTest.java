package com.example.board.comment.service;

import com.example.board.comment.domain.Comment;
import com.example.board.comment.dto.CommentThread;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    // ── update ────────────────────────────────────────────────

    @Test
    @DisplayName("본인 댓글은 내용을 고칠 수 있다")
    void update_owner() {
        Comment comment = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "오타가 있는 댓글");
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        Comment updated = commentService.update(5L, COMMENTER_ID, "고친 댓글");

        assertThat(updated.getContent()).isEqualTo("고친 댓글");
    }

    @Test
    @DisplayName("타인의 댓글을 고치려 하면 AccessDeniedException 이 발생한다")
    void update_notOwner_denied() {
        Comment comment = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "남의 댓글");
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(5L, 999L, "가로채기"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(comment.getContent()).isEqualTo("남의 댓글");
    }

    @Test
    @DisplayName("댓글 수정은 알림을 다시 보내지 않는다 - 오타를 고칠 때마다 알리면 그게 소음이다")
    void update_doesNotNotify() {
        Comment comment = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "처음 쓴 댓글");
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        commentService.update(5L, COMMENTER_ID, "고친 댓글");

        then(eventPublisher).should(never()).publishEvent(any(CommentAddedEvent.class));
    }


    // ── 답글 ──────────────────────────────────────────────────

    @Test
    @DisplayName("답글은 원댓글과 같은 글에 매달린다")
    void reply_attachesToSameTarget() {
        Post target = post();
        ReflectionTestUtils.setField(target, "id", 1L);
        Comment root = Comment.forPost(target, member(POST_AUTHOR_ID, "글쓴이"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        given(commentRepository.findById(5L)).willReturn(Optional.of(root));
        given(memberRepository.getReferenceById(COMMENTER_ID)).willReturn(member(COMMENTER_ID, "댓글러"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.reply(5L, COMMENTER_ID, "저도 궁금해요");

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        then(commentRepository).should().save(saved.capture());
        assertThat(saved.getValue().getParent()).isSameAs(root);
        assertThat(saved.getValue().getPost()).isSameAs(target);
        assertThat(saved.getValue().isReply()).isTrue();
    }

    @Test
    @DisplayName("답글에 답글을 달면 원댓글에 붙는다 - 깊이는 1단계다")
    void reply_toReply_flattensToRoot() {
        // 깊이를 열어 두면 화면이 오른쪽으로 계속 밀리고, 스레드 모양이 하나로 유지되지 않는다
        Post target = post();
        Comment root = Comment.forPost(target, member(POST_AUTHOR_ID, "글쓴이"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        Comment firstReply = Comment.replyTo(root, member(COMMENTER_ID, "댓글러"), "답글");
        ReflectionTestUtils.setField(firstReply, "id", 6L);
        given(commentRepository.findById(6L)).willReturn(Optional.of(firstReply));
        given(memberRepository.getReferenceById(30L)).willReturn(member(30L, "제삼자"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.reply(6L, 30L, "답글의 답글");

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        then(commentRepository).should().save(saved.capture());
        assertThat(saved.getValue().getParent()).isSameAs(root);
    }

    @Test
    @DisplayName("답글 알림은 원댓글 작성자에게 간다 - 글쓴이는 원댓글 때 이미 받았다")
    void reply_notifiesRootAuthor() {
        Post target = post();
        ReflectionTestUtils.setField(target, "id", 1L);
        Comment root = Comment.forPost(target, member(COMMENTER_ID, "댓글러"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        given(commentRepository.findById(5L)).willReturn(Optional.of(root));
        given(memberRepository.getReferenceById(30L)).willReturn(member(30L, "제삼자"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.reply(5L, 30L, "저도요");

        ArgumentCaptor<CommentAddedEvent> event = ArgumentCaptor.forClass(CommentAddedEvent.class);
        then(eventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(COMMENTER_ID);
    }

    @Test
    @DisplayName("내 댓글에 내가 단 답글은 알리지 않는다")
    void reply_selfDoesNotNotify() {
        Post target = post();
        ReflectionTestUtils.setField(target, "id", 1L);
        Comment root = Comment.forPost(target, member(COMMENTER_ID, "댓글러"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        given(commentRepository.findById(5L)).willReturn(Optional.of(root));
        given(memberRepository.getReferenceById(COMMENTER_ID)).willReturn(member(COMMENTER_ID, "댓글러"));
        given(commentRepository.save(any(Comment.class))).willAnswer(inv -> inv.getArgument(0));

        commentService.reply(5L, COMMENTER_ID, "덧붙이자면");

        then(eventPublisher).should(never()).publishEvent(any(CommentAddedEvent.class));
    }

    @Test
    @DisplayName("볼 수 없는 플랜의 댓글에는 답글을 달 수 없다")
    void reply_hiddenPlan_denied() {
        Plan hidden = sharedPlan();
        Comment root = Comment.forPlan(hidden, member(POST_AUTHOR_ID, "글쓴이"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        given(commentRepository.findById(5L)).willReturn(Optional.of(root));
        given(planService.canView(hidden, COMMENTER_ID)).willReturn(false);

        assertThatThrownBy(() -> commentService.reply(5L, COMMENTER_ID, "끼어들기"))
                .isInstanceOf(AccessDeniedException.class);
        then(commentRepository).should(never()).save(any(Comment.class));
    }

    // ── 삭제와 답글 ───────────────────────────────────────────

    @Test
    @DisplayName("원댓글을 지우면 달려 있던 답글도 함께 지운다")
    void delete_rootRemovesReplies() {
        Comment root = Comment.forPost(post(), member(COMMENTER_ID, "댓글러"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        List<Comment> replies = List.of(Comment.replyTo(root, member(30L, "제삼자"), "답글"));
        given(commentRepository.findById(5L)).willReturn(Optional.of(root));
        given(commentRepository.findByParent_IdOrderByIdAsc(5L)).willReturn(replies);

        commentService.delete(5L, COMMENTER_ID);

        // DB 의 on delete cascade 에 기대면 엔티티로 스키마를 만드는 테스트에서는 돌지 않는다
        then(commentRepository).should().deleteAll(replies);
        then(commentRepository).should().delete(root);
    }

    @Test
    @DisplayName("답글을 지울 때는 자식을 찾지 않는다 - 답글에는 자식이 없다")
    void delete_replyDoesNotLookForChildren() {
        Comment root = Comment.forPost(post(), member(POST_AUTHOR_ID, "글쓴이"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        Comment reply = Comment.replyTo(root, member(COMMENTER_ID, "댓글러"), "답글");
        ReflectionTestUtils.setField(reply, "id", 6L);
        given(commentRepository.findById(6L)).willReturn(Optional.of(reply));

        commentService.delete(6L, COMMENTER_ID);

        then(commentRepository).should(never()).findByParent_IdOrderByIdAsc(anyLong());
        then(commentRepository).should().delete(reply);
    }

    // ── 스레드 묶기 ───────────────────────────────────────────

    @Test
    @DisplayName("원댓글과 답글을 스레드로 묶어 준다")
    void findForPost_groupsReplies() {
        Comment root = Comment.forPost(post(), member(POST_AUTHOR_ID, "글쓴이"), "원댓글");
        ReflectionTestUtils.setField(root, "id", 5L);
        Comment reply = Comment.replyTo(root, member(COMMENTER_ID, "댓글러"), "답글");
        ReflectionTestUtils.setField(reply, "id", 6L);
        Pageable pageable = PageRequest.of(0, 20);
        given(commentRepository.findByPost_IdAndParentIsNullOrderByIdAsc(1L, pageable))
                .willReturn(new PageImpl<>(List.of(root)));
        given(commentRepository.findByParent_IdInOrderByIdAsc(List.of(5L)))
                .willReturn(List.of(reply));

        Page<CommentThread> threads = commentService.findForPost(1L, pageable);

        assertThat(threads.getContent()).hasSize(1);
        assertThat(threads.getContent().get(0).root()).isSameAs(root);
        assertThat(threads.getContent().get(0).replies()).containsExactly(reply);
    }

    @Test
    @DisplayName("원댓글이 없으면 답글을 조회하지 않는다 - 빈 in 절은 쿼리 오류다")
    void findForPost_emptyPageSkipsReplyQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        given(commentRepository.findByPost_IdAndParentIsNullOrderByIdAsc(1L, pageable))
                .willReturn(new PageImpl<>(List.of()));

        assertThat(commentService.findForPost(1L, pageable).getContent()).isEmpty();

        then(commentRepository).should(never()).findByParent_IdInOrderByIdAsc(any());
    }

}
