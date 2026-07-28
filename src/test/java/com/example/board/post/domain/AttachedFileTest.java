package com.example.board.post.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AttachedFileTest {

    @Test
    @DisplayName("contentType 이 image/* 이면 isImage() 가 true 를 반환한다")
    void isImage_true() {
        AttachedFile file = new AttachedFile("photo.png", "uuid.png", "image/png", 1024L);
        assertThat(file.isImage()).isTrue();
    }

    @Test
    @DisplayName("contentType 이 image/* 가 아니면 isImage() 가 false 를 반환한다")
    void isImage_false() {
        AttachedFile file = new AttachedFile("doc.pdf", "uuid.pdf", "application/pdf", 2048L);
        assertThat(file.isImage()).isFalse();
    }

    @Test
    @DisplayName("contentType 이 null 이면 isImage() 가 false 를 반환한다")
    void isImage_nullContentType() {
        AttachedFile file = new AttachedFile("unknown", "uuid", null, 0L);
        assertThat(file.isImage()).isFalse();
    }
}
