package com.example.board.post.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    /** 마크다운 원본. 화면에 넣을 HTML 은 읽을 때마다 만든다 (정책이 바뀌면 옛 글에도 적용되도록) */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category = PostCategory.FREE;

    /** 조회수 - 목록에서 인기 글을 가늠하는 값 */
    @Column(nullable = false)
    private int viewCount;

    /** 좋아요 수. 진실은 post_like 표에 있고 이 값은 목록에서 매번 세지 않으려고 함께 든다 */
    @Column(nullable = false)
    private int likeCount;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttachedFile> files = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** 분류를 정하지 않은 글은 자유 게시글로 본다 (분류가 없던 시절의 글과 같은 자리) */
    public Post(String title, String content, Member author) {
        this(title, content, author, PostCategory.FREE);
    }

    public Post(String title, String content, Member author, PostCategory category) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = (category != null) ? category : PostCategory.FREE;
    }

    /** 현재 사용자가 이 글의 작성자인지 확인한다 */
    public boolean isAuthoredBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void update(String title, String content) {
        update(title, content, this.category);
    }

    public void update(String title, String content, PostCategory category) {
        this.title = title;
        this.content = content;
        this.category = (category != null) ? category : PostCategory.FREE;
    }

    /**
     * 조회수를 올린다. 작성자 본인의 조회는 세지 않는다 -
     * 글을 쓴 사람이 확인하러 들어온 것까지 세면 숫자가 남의 관심을 뜻하지 않게 된다.
     *
     * <p>같은 사람이 새로고침하면 또 오른다는 한계가 있다. 이를 막으려면 본 사람을 행으로 남겨야 하는데,
     * 게시판 읽기는 비로그인도 열려 있어 누구인지 식별할 수 없는 조회가 섞인다.</p>
     */
    public void increaseViewCount(Long viewerId) {
        if (viewerId != null && isAuthoredBy(viewerId)) {
            return;
        }
        this.viewCount++;
    }

    /** 좋아요 표가 늘고 줄 때 카운터를 맞춘다 (같은 트랜잭션 안에서만 부른다) */
    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
    }

    public void addFile(AttachedFile file) {
        files.add(file);
        file.setPost(this);
    }
}
