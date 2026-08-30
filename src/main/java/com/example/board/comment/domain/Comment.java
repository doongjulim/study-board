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

    @Column(nullable = false, length = 500)
    private String content;

    @CreatedDate
    private LocalDateTime createdAt;

    /** null 이면 한 번도 고치지 않았다는 뜻이다 (그래서 화면이 '수정됨' 을 붙일지 판단할 수 있다) */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    private Comment(Post post, Plan plan, Member author, String content) {
        this.post = post;
        this.plan = plan;
        this.author = author;
        this.content = content;
    }

    public static Comment forPost(Post post, Member author, String content) {
        return new Comment(post, null, author, content);
    }

    public static Comment forPlan(Plan plan, Member author, String content) {
        return new Comment(null, plan, author, content);
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
