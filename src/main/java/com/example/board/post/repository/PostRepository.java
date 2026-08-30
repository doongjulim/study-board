package com.example.board.post.repository;

import com.example.board.post.domain.Post;
import com.example.board.post.domain.PostCategory;
import com.example.board.post.dto.PostSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 목록은 {@link PostSummary} 프로젝션으로,
 * 상세는 {@link EntityGraph} 로 author·files 를 함께 로딩한다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    String SUMMARY_SELECT = "select new com.example.board.post.dto.PostSummary(" +
            "p.id, p.title, p.author.nickname, p.category, p.createdAt, size(p.files), " +
            "p.viewCount, p.likeCount) from Post p";

    /** 분류 조건은 어떤 검색과도 함께 걸릴 수 있어야 하므로, null 이면 통과시키는 형태로 붙인다 */
    String CATEGORY_FILTER = " and (:category is null or p.category = :category)";

    @Override
    @EntityGraph(attributePaths = {"author", "files"})
    Optional<Post> findById(Long id);

    @Query(SUMMARY_SELECT + " where 1 = 1" + CATEGORY_FILTER)
    Page<PostSummary> findSummaries(@Param("category") PostCategory category, Pageable pageable);

    @Query(SUMMARY_SELECT + " where lower(p.title) like lower(concat('%', :keyword, '%'))"
            + CATEGORY_FILTER)
    Page<PostSummary> findSummariesByTitle(@Param("keyword") String keyword,
                                           @Param("category") PostCategory category, Pageable pageable);

    // 제목·내용 조건은 괄호로 묶는다 - 묶지 않으면 or 가 분류 조건까지 흡수한다
    @Query(SUMMARY_SELECT + " where (lower(p.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(p.content) like lower(concat('%', :keyword, '%')))" + CATEGORY_FILTER)
    Page<PostSummary> findSummariesByTitleOrContent(@Param("keyword") String keyword,
                                                    @Param("category") PostCategory category,
                                                    Pageable pageable);

    @Query(SUMMARY_SELECT + " where lower(p.author.nickname) like lower(concat('%', :keyword, '%'))"
            + CATEGORY_FILTER)
    Page<PostSummary> findSummariesByAuthorNickname(@Param("keyword") String keyword,
                                                     @Param("category") PostCategory category,
                                                     Pageable pageable);

    // ── 모아보기 ──────────────────────────────────────────────
    // 내가 쓴 글과 내가 좋아요한 글은 게시판 전체에서 검색으로 찾아내는 것이 아니라
    // 바로 가는 길이 있어야 한다 (닉네임 검색은 동명이인·닉네임 변경에 흔들린다).

    /** 내가 쓴 글 (최신순 - 정렬을 쿼리에 두므로 Pageable 은 정렬 없이 넘긴다) */
    @Query(SUMMARY_SELECT + " where p.author.id = :authorId order by p.id desc")
    Page<PostSummary> findSummariesByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    /**
     * 내가 좋아요한 글.
     *
     * <p>PostLike 를 기준으로 조인한다 - Post 에서 시작해 exists 로 거르는 것보다
     * "내가 누른 표" 라는 사실에 가깝고, 표가 적은 쪽에서 출발해 훑는 범위도 작다.</p>
     */
    @Query("""
            select new com.example.board.post.dto.PostSummary(
                p.id, p.title, p.author.nickname, p.category, p.createdAt, size(p.files),
                p.viewCount, p.likeCount)
            from PostLike liked join liked.post p
            where liked.member.id = :memberId
            order by liked.id desc
            """)
    Page<PostSummary> findLikedSummaries(@Param("memberId") Long memberId, Pageable pageable);
}
