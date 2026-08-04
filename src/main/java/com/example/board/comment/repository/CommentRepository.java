package com.example.board.comment.repository;

import com.example.board.comment.domain.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findByPost_IdOrderByIdAsc(Long postId);

    @EntityGraph(attributePaths = "author")
    List<Comment> findByPlan_IdOrderByIdAsc(Long planId);
}
