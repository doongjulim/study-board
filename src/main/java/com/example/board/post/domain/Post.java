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

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttachedFile> files = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Post(String title, String content, Member author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    /** 현재 사용자가 이 글의 작성자인지 확인한다 */
    public boolean isAuthoredBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void addFile(AttachedFile file) {
        files.add(file);
        file.setPost(this);
    }
}
