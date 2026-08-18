package com.example.board.post.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 좋아요. 누가 눌렀는지를 행으로 남긴다.
 *
 * <p>숫자만 세는 컬럼 하나로는 "내가 눌렀는지" 를 알 수 없어 두 번 누를 수 있게 된다.
 * 누구인지 남겨 두고 (post, member) 를 유니크로 묶으면, 동시에 두 번 눌러도 DB 가 막아 준다.</p>
 *
 * <p>{@code Post.likeCount} 는 이 행들을 매번 세지 않으려고 함께 들고 있는 값이다.
 * 진실은 이 표에 있고, 카운터는 같은 트랜잭션 안에서만 움직인다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "post_like", uniqueConstraints =
        @UniqueConstraint(name = "uq_post_like", columnNames = {"post_id", "member_id"}))
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @CreatedDate
    private LocalDateTime createdAt;

    public PostLike(Post post, Member member) {
        this.post = post;
        this.member = member;
    }
}
