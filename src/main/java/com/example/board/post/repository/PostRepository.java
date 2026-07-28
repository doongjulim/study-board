package com.example.board.post.repository;

import com.example.board.post.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Post> findByWriterContainingIgnoreCase(String keyword, Pageable pageable);

    @Query("select p from Post p " +
            "where lower(p.title) like lower(concat('%', :keyword, '%')) " +
            "or lower(p.content) like lower(concat('%', :keyword, '%'))")
    Page<Post> searchByTitleOrContent(@Param("keyword") String keyword, Pageable pageable);
}
