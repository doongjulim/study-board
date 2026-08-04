package com.example.board.comment.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.post.domain.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
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
}
