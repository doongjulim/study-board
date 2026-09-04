package com.example.board.comment.service;

import com.example.board.comment.domain.Comment;
import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.comment.dto.CommentThread;
import com.example.board.comment.repository.CommentRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.service.PlanService;
import com.example.board.post.domain.Post;
import com.example.board.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 게시글의 댓글 스레드 한 페이지.
     *
     * <p>원댓글로 페이지를 나눈 다음 그 원댓글들의 답글만 한 번에 가져와 묶는다.
     * 답글을 원댓글마다 따로 조회하면 한 페이지에 스무 번의 쿼리가 나간다.</p>
     */
    public Page<CommentThread> findForPost(Long postId, Pageable pageable) {
        return toThreads(commentRepository.findByPost_IdAndParentIsNullOrderByIdAsc(postId, pageable));
    }

    public Page<CommentThread> findForPlan(Long planId, Pageable pageable) {
        return toThreads(commentRepository.findByPlan_IdAndParentIsNullOrderByIdAsc(planId, pageable));
    }

    /** 화면의 "댓글 N" 은 답글까지 센다 - 페이지에 보이는 수가 아니라 전체 수다 */
    public long countForPost(Long postId) {
        return commentRepository.countByPost_Id(postId);
    }

    public long countForPlan(Long planId) {
        return commentRepository.countByPlan_Id(planId);
    }

    private Page<CommentThread> toThreads(Page<Comment> roots) {
        List<Long> rootIds = roots.getContent().stream().map(Comment::getId).toList();
        if (rootIds.isEmpty()) {
            return roots.map(root -> new CommentThread(root, List.of()));
        }
        Map<Long, List<Comment>> repliesByRoot = commentRepository.findByParent_IdInOrderByIdAsc(rootIds)
                .stream()
                .collect(Collectors.groupingBy(reply -> reply.getParent().getId()));
        return roots.map(root -> new CommentThread(root,
                repliesByRoot.getOrDefault(root.getId(), List.of())));
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
     * 답글을 단다.
     *
     * <p>자격은 원댓글이 달린 대상이 정한다 - 공유 플랜의 댓글이면 그 플랜을 볼 수 있어야 하고,
     * 게시글이면 로그인만으로 충분하다(게시판 쓰기 규칙과 같다).
     * 알림은 <b>원댓글 작성자</b>에게 간다 - 글쓴이는 원댓글 때 이미 받았다.</p>
     */
    @Transactional
    public Long reply(Long targetCommentId, Long memberId, String content) {
        Comment target = findById(targetCommentId);
        if (!target.isForPost() && !planService.canView(target.getPlan(), memberId)) {
            throw new AccessDeniedException("볼 수 있는 플랜에만 답글을 달 수 있습니다.");
        }
        Member commenter = memberRepository.getReferenceById(memberId);
        Comment saved = commentRepository.save(Comment.replyTo(target, commenter, content));

        Comment root = saved.getParent();
        if (!root.isAuthoredBy(memberId)) { // 내 댓글에 내가 단 답글은 알리지 않는다
            eventPublisher.publishEvent(new CommentAddedEvent(
                    root.getAuthor().getId(), commenter.getNickname(),
                    root.getContent(), urlOf(root)));
        }
        return saved.getId();
    }

    /** 댓글이 달려 있는 화면의 주소 */
    private String urlOf(Comment comment) {
        return comment.isForPost()
                ? "/posts/" + comment.getPost().getId()
                : "/plans/shared/" + comment.getPlan().getId();
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

    /**
     * 본인 댓글만 삭제할 수 있다. 삭제된 댓글을 반환해 컨트롤러가 돌아갈 화면을 정한다.
     *
     * <p>원댓글을 지우면 답글도 함께 사라진다. 남의 답글까지 지우는 셈이라 화면에서 미리 알린다.
     * 대안은 "삭제된 댓글입니다" 로 껍데기를 남기는 것인데, 그러면 모든 읽기 경로가
     * 그 상태를 알아야 한다 - 스레드를 얕게(1단계) 유지하기로 한 것과 같은 이유로 택하지 않았다.</p>
     *
     * <p>답글을 코드로 먼저 지운다. DB 의 on delete cascade 에 기대면 엔티티로 스키마를 만드는
     * 테스트에는 그 규칙이 없어 운영에서만 도는 코드가 된다.</p>
     */
    @Transactional
    public Comment delete(Long commentId, Long memberId) {
        Comment comment = findOwned(commentId, memberId, "삭제");
        if (!comment.isReply()) {
            commentRepository.deleteAll(commentRepository.findByParent_IdOrderByIdAsc(commentId));
        }
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
