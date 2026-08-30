package com.example.board.comment.service;

import com.example.board.comment.domain.Comment;
import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.comment.repository.CommentRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.service.PlanService;
import com.example.board.post.domain.Post;
import com.example.board.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    /** 플랜을 볼 자격 판정은 plan 모듈이 갖는다 - 규칙이 두 곳에 생기지 않게 읽기 전용으로만 쓴다 */
    private final PlanService planService;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 댓글 하나. 수정 실패 후 돌아갈 화면을 정할 때처럼 대상만 알면 되는 자리에서 쓴다 */
    public Comment findById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다. id=" + commentId));
    }

    public List<Comment> findForPost(Long postId) {
        return commentRepository.findByPost_IdOrderByIdAsc(postId);
    }

    public List<Comment> findForPlan(Long planId) {
        return commentRepository.findByPlan_IdOrderByIdAsc(planId);
    }

    @Transactional
    public Long addToPost(Long postId, Long memberId, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다. id=" + postId));
        Member commenter = memberRepository.getReferenceById(memberId);
        Comment saved = commentRepository.save(Comment.forPost(post, commenter, content));

        if (!post.isAuthoredBy(memberId)) { // 내 글에 내가 단 댓글은 알리지 않는다
            eventPublisher.publishEvent(new CommentAddedEvent(
                    post.getAuthor().getId(), commenter.getNickname(), post.getTitle(), "/posts/" + postId));
        }
        return saved.getId();
    }

    @Transactional
    public Long addToPlan(Long planId, Long memberId, String content) {
        Plan plan = planService.findById(planId);
        // 볼 수 없는 플랜에는 댓글도 달 수 없다 (그룹 공개 플랜은 같은 그룹 사람까지)
        if (!planService.canView(plan, memberId)) {
            throw new AccessDeniedException("공유된 플랜에만 댓글을 달 수 있습니다.");
        }
        Member commenter = memberRepository.getReferenceById(memberId);
        Comment saved = commentRepository.save(Comment.forPlan(plan, commenter, content));

        if (!plan.isAuthoredBy(memberId)) {
            eventPublisher.publishEvent(new CommentAddedEvent(
                    plan.getAuthor().getId(), commenter.getNickname(), plan.getTitle(),
                    "/plans/shared/" + planId));
        }
        return saved.getId();
    }

    /**
     * 본인 댓글만 고칠 수 있다.
     *
     * <p>고친 댓글을 반환하는 것은 삭제와 같은 이유다 - 컨트롤러가 돌아갈 화면(글이냐 플랜이냐)을
     * 정하려면 대상이 필요하고, 그 판단을 컨트롤러가 다시 조회해서 하게 만들 이유가 없다.</p>
     *
     * <p>수정은 알림을 보내지 않는다. 이미 한 번 알린 댓글이고, 오타를 고칠 때마다
     * 글쓴이에게 알림이 다시 가면 그게 곧 소음이다.</p>
     */
    @Transactional
    public Comment update(Long commentId, Long memberId, String content) {
        Comment comment = findOwned(commentId, memberId, "수정");
        comment.updateContent(content);
        return comment;
    }

    /** 본인 댓글만 삭제할 수 있다. 삭제된 댓글을 반환해 컨트롤러가 돌아갈 화면을 정한다 */
    @Transactional
    public Comment delete(Long commentId, Long memberId) {
        Comment comment = findOwned(commentId, memberId, "삭제");
        commentRepository.delete(comment);
        return comment;
    }

    private Comment findOwned(Long commentId, Long memberId, String action) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다. id=" + commentId));
        if (!comment.isAuthoredBy(memberId)) {
            throw new AccessDeniedException("본인의 댓글만 %s할 수 있습니다.".formatted(action));
        }
        return comment;
    }
}
