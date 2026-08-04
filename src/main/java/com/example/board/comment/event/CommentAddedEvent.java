package com.example.board.comment.event;

/** 댓글이 달렸을 때 발행 - notification 모듈이 구독해 대상 글/플랜 작성자에게 알린다 */
public record CommentAddedEvent(Long recipientId, String commenterNickname, String targetTitle, String url) {
}
