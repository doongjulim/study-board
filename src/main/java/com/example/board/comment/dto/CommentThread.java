package com.example.board.comment.dto;

import com.example.board.comment.domain.Comment;

import java.util.List;

/**
 * 원댓글 하나와 거기 달린 답글들.
 *
 * <p>화면이 다루는 단위를 그대로 값으로 만든다. 이것이 없으면 템플릿이 전체 댓글 목록에서
 * "부모가 나인 것" 을 매번 걸러내야 하고, 그 규칙이 게시글 화면과 플랜 화면 두 곳에 생긴다.</p>
 *
 * @param root    원댓글
 * @param replies 답글 (등록 순)
 */
public record CommentThread(Comment root, List<Comment> replies) {

    public boolean hasReplies() {
        return !replies.isEmpty();
    }

    public int size() {
        return 1 + replies.size();
    }
}
