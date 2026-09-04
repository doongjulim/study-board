package com.example.board.comment.repository;

import com.example.board.comment.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 댓글 조회는 <b>원댓글 페이지 → 그 답글들</b> 두 걸음이다.
 *
 * <p>스레드를 통째로 페이징하면 한 페이지에 원댓글 절반과 답글 일부가 걸려 스레드가 잘린다.
 * 페이지의 단위는 스레드이므로, 원댓글만 세어서 나누고 답글은 그 원댓글들 것만 따로 가져온다.</p>
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 게시글의 원댓글 (답글 제외) - 페이지의 단위 */
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPost_IdAndParentIsNullOrderByIdAsc(Long postId, Pageable pageable);

    /** 공유 플랜의 원댓글 */
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByPlan_IdAndParentIsNullOrderByIdAsc(Long planId, Pageable pageable);

    /** 이 원댓글들에 달린 답글 - 빈 컬렉션은 in () 구문 오류라 부르는 쪽에서 걸러야 한다 */
    @EntityGraph(attributePaths = "author")
    List<Comment> findByParent_IdInOrderByIdAsc(Collection<Long> parentIds);

    /** 한 원댓글의 답글 - 삭제할 때 함께 지우기 위해 필요하다 */
    @EntityGraph(attributePaths = "author")
    List<Comment> findByParent_IdOrderByIdAsc(Long parentId);

    /** 스레드 수(답글 포함) - 화면의 "댓글 N" 은 답글까지 센다 */
    long countByPost_Id(Long postId);

    long countByPlan_Id(Long planId);
}
