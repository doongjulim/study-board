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
}
