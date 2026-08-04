package com.example.board.post.repository;

import com.example.board.post.domain.Post;
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
            "p.id, p.title, p.author.nickname, p.createdAt, size(p.files)) from Post p";

    @Override
    @EntityGraph(attributePaths = {"author", "files"})
    Optional<Post> findById(Long id);

    @Query(SUMMARY_SELECT)
    Page<PostSummary> findSummaries(Pageable pageable);

    @Query(SUMMARY_SELECT + " where lower(p.title) like lower(concat('%', :keyword, '%'))")
    Page<PostSummary> findSummariesByTitle(@Param("keyword") String keyword, Pageable pageable);

    @Query(SUMMARY_SELECT + " where lower(p.title) like lower(concat('%', :keyword, '%')) " +
            "or lower(p.content) like lower(concat('%', :keyword, '%'))")
    Page<PostSummary> findSummariesByTitleOrContent(@Param("keyword") String keyword, Pageable pageable);

    @Query(SUMMARY_SELECT + " where lower(p.author.nickname) like lower(concat('%', :keyword, '%'))")
    Page<PostSummary> findSummariesByAuthorNickname(@Param("keyword") String keyword, Pageable pageable);
}
