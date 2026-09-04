package com.example.board.comment.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.post.domain.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 게시글 또는 공유 플랜에 달리는 댓글.
 * post/plan 중 정확히 하나만 채워진다 (DB check 제약으로도 보장).
 */
@Entity
@Table(name = "comments") // comment 는 SQL 예약어와 충돌 소지가 있어 복수형 사용
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    /**
     * 답글이면 원댓글을 가리킨다. 원댓글이면 null.
     *
     * <p>깊이는 1단계까지다 - 답글의 답글은 원댓글에 붙는다({@link #rootOf}).
     * 화면이 오른쪽으로 끝없이 밀리는 것을 막고, 스레드를 "원댓글 + 답글 목록" 이라는
     * 한 가지 모양으로 유지하기 위해서다.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 500)
    private String content;

    @CreatedDate
    private LocalDateTime createdAt;

    /** null 이면 한 번도 고치지 않았다는 뜻이다 (그래서 화면이 '수정됨' 을 붙일지 판단할 수 있다) */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    private Comment(Post post, Plan plan, Member author, String content, Comment parent) {
        this.post = post;
        this.plan = plan;
        this.author = author;
        this.content = content;
        this.parent = parent;
    }

    public static Comment forPost(Post post, Member author, String content) {
        return new Comment(post, null, author, content, null);
    }

    public static Comment forPlan(Plan plan, Member author, String content) {
        return new Comment(null, plan, author, content, null);
    }

    /**
     * 답글을 만든다. 대상이 이미 답글이면 그 원댓글에 붙는다 - 깊이는 1단계까지다.
     *
     * <p>대상과 같은 글/플랜에 매달아야 한다. 답글만 다른 글에 붙으면 스레드가 갈라진다.</p>
     */
    public static Comment replyTo(Comment target, Member author, String content) {
        Comment root = rootOf(target);
        return new Comment(root.post, root.plan, author, content, root);
    }

    private static Comment rootOf(Comment comment) {
        return comment.isReply() ? comment.parent : comment;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isAuthoredBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public boolean isForPost() {
        return post != null;
    }

    /** 내용을 고친다. 대상(글/플랜)과 작성자는 바뀌지 않는다 */
    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * 고친 적이 있는가.
     *
     * <p>@LastModifiedDate 는 저장할 때마다 채워지므로, 처음 등록한 순간에도 값이 들어간다.
     * 그래서 "값이 있는가" 가 아니라 "만든 시각과 다른가" 로 판단한다 -
     * 그러지 않으면 모든 댓글에 '수정됨' 이 붙는다.</p>
     */
    public boolean isEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }
}
