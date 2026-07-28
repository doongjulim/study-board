package com.example.board.post.dto;

import java.time.LocalDateTime;

/**
 * 게시글 목록 전용 프로젝션.
 * open-in-view 가 꺼진 상태에서 연관 엔티티(lazy) 접근 없이 화면에 필요한 값만 담고,
 * 컬렉션 fetch join 없이 DB 페이징을 유지한다.
 */
public record PostSummary(Long id, String title, String authorNickname,
                          LocalDateTime createdAt, long fileCount) {
}
