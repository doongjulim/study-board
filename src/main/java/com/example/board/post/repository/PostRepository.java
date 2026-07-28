package com.example.board.post.repository;

import com.example.board.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면에 노출되는 조회는
 * {@link EntityGraph} 로 author(·files)를 함께 로딩한다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "files"})
    Optional<Post> findById(Long id);

    @EntityGraph(attributePaths = "author")
    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<Post> findByAuthor_NicknameContainingIgnoreCase(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("select p from Post p " +
            "where lower(p.title) like lower(concat('%', :keyword, '%')) " +
            "or lower(p.content) like lower(concat('%', :keyword, '%'))")
    Page<Post> searchByTitleOrContent(@Param("keyword") String keyword, Pageable pageable);
}
