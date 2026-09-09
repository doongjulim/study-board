package com.example.board.comment.domain;

import com.example.board.member.domain.Member;
import com.example.board.post.domain.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CommentTest {

    private final Member author = new Member("writer", "encoded", "글쓴이");

    private Comment comment(String content) {
        return Comment.forPost(new Post("제목", "본문", author), author, content);
    }

    // ── 앞부분(excerpt) ───────────────────────────────────────

    @Test
    @DisplayName("짧은 댓글은 그대로 돌려준다")
    void excerpt_short() {
        assertThat(comment("좋은 글이네요").excerpt()).isEqualTo("좋은 글이네요");
    }

    @Test
    @DisplayName("긴 댓글은 앞부분만 자르고 말줄임표를 붙인다")
    void excerpt_long() {
        String excerpt = comment("가".repeat(500)).excerpt();

        assertThat(excerpt).hasSize(41).endsWith("…");
    }

    @Test
    @DisplayName("줄바꿈은 공백으로 편다 - 알림은 한 줄로 보이는 자리다")
    void excerpt_flattensNewlines() {
        assertThat(comment("첫 줄\n\n둘째 줄").excerpt()).isEqualTo("첫 줄 둘째 줄");
    }

    // ── 깊이 ─────────────────────────────────────────────────

    @Test
    @DisplayName("답글의 답글은 원댓글에 붙는다 - 깊이는 1단계다")
    void replyTo_flattensToRoot() {
        Comment root = comment("원댓글");
        Comment reply = Comment.replyTo(root, author, "답글");

        Comment replyToReply = Comment.replyTo(reply, author, "답글의 답글");

        assertThat(replyToReply.getParent()).isSameAs(root);
    }

    @Test
    @DisplayName("답글은 원댓글과 같은 글에 매달린다 - 스레드가 갈라지지 않는다")
    void replyTo_sharesTarget() {
        Comment root = comment("원댓글");

        Comment reply = Comment.replyTo(root, author, "답글");

        assertThat(reply.getPost()).isSameAs(root.getPost());
        assertThat(reply.getPlan()).isNull();
    }
}
