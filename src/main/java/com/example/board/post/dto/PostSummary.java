package com.example.board.post.dto;

import com.example.board.post.domain.PostCategory;

import java.time.LocalDateTime;

/**
 * 게시글 목록 전용 프로젝션.
 * open-in-view 가 꺼진 상태에서 연관 엔티티(lazy) 접근 없이 화면에 필요한 값만 담고,
 * 컬렉션 fetch join 없이 DB 페이징을 유지한다.
 *
 * <p>좋아요·조회수는 Post 가 함께 든 카운터를 그대로 읽는다 - 목록에서 글마다 세면
 * 한 페이지에 스무 번의 count 쿼리가 나간다.</p>
 */
public record PostSummary(Long id, String title, String authorNickname, PostCategory category,
                          LocalDateTime createdAt, long fileCount, int viewCount, int likeCount) {
}
