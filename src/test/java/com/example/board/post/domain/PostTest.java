package com.example.board.post.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PostTest {

    private Member author() {
        return new Member("tester1", "encoded-password", "작성자");
    }

    @Test
    @DisplayName("Post 생성 시 제목·내용·작성자가 설정된다")
    void create() {
        Post post = new Post("제목", "내용", author());

        assertThat(post.getTitle()).isEqualTo("제목");
        assertThat(post.getContent()).isEqualTo("내용");
        assertThat(post.getAuthor().getNickname()).isEqualTo("작성자");
        assertThat(post.getFiles()).isEmpty();
    }

    @Test
    @DisplayName("update() 는 제목과 내용만 변경하고 작성자는 유지한다")
    void update() {
        Member author = author();
        Post post = new Post("원래제목", "원래내용", author);

        post.update("새제목", "새내용");

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("새내용");
        assertThat(post.getAuthor()).isSameAs(author);
    }

    @Test
    @DisplayName("addFile() 은 파일을 추가하고 양방향 연관관계를 설정한다")
    void addFile() {
        Post post = new Post("제목", "내용", author());
        AttachedFile file = new AttachedFile("test.txt", "uuid.txt", "text/plain", 100L);

        post.addFile(file);

        assertThat(post.getFiles()).hasSize(1);
        assertThat(file.getPost()).isSameAs(post);
    }
}
