package com.example.board.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttachedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자가 업로드한 원본 파일명 */
    @Column(nullable = false)
    private String originalName;

    /** 서버에 저장된 파일명 (UUID) */
    @Column(nullable = false, unique = true)
    private String storedName;

    private String contentType;

    private long size;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    public AttachedFile(String originalName, String storedName, String contentType, long size) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.size = size;
    }

    void setPost(Post post) {
        this.post = post;
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image");
    }
}
