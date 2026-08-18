package com.example.board.post.repository;

import com.example.board.post.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPost_IdAndMember_Id(Long postId, Long memberId);

    boolean existsByPost_IdAndMember_Id(Long postId, Long memberId);

    /**
     * 목록에 보이는 글들 중 내가 누른 것 - 글마다 한 번씩 묻지 않도록 한 번에 가져온다.
     * 빈 컬렉션은 {@code in ()} 구문 오류가 되므로 부르는 쪽에서 걸러야 한다.
     *
     * <p>별칭을 like 로 두지 않는 이유는 HQL 에서 like 가 연산자 키워드이기 때문이다.</p>
     */
    @Query("""
            select liked.post.id from PostLike liked
            where liked.member.id = :memberId and liked.post.id in :postIds
            """)
    List<Long> findLikedPostIds(@Param("memberId") Long memberId,
                               @Param("postIds") Collection<Long> postIds);

    /** 회원 탈퇴 시 개인 흔적 정리 */
    void deleteByMember_Id(Long memberId);
}
