package com.example.board.group.repository;

import com.example.board.group.domain.GroupMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면에 노출되는 조회는
 * {@link EntityGraph} 로 연관 엔티티를 함께 로딩한다.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    /** 내가 속한 그룹 목록 화면 - 그룹을 함께 로딩한다 */
    @EntityGraph(attributePaths = "studyGroup")
    List<GroupMember> findByMember_IdOrderByIdAsc(Long memberId);

    /** 그룹 멤버 목록 화면·그룹장 승계 - 가입이 오래된 순서 */
    @EntityGraph(attributePaths = "member")
    List<GroupMember> findByStudyGroup_IdOrderByJoinedAtAscIdAsc(Long groupId);

    Optional<GroupMember> findByStudyGroup_IdAndMember_Id(Long groupId, Long memberId);

    /**
     * 그룹을 지우기 전에 소속 행을 먼저 치운다.
     *
     * <p>DB 에도 {@code on delete cascade} 가 걸려 있지만 거기에 기대지 않는다.
     * ORM 은 그 정리를 모르므로 같은 트랜잭션 안에서 이미 사라진 행을 계속 들고 있고,
     * 무엇보다 <b>그 규칙은 마이그레이션에만 있다</b> - 엔티티에서 스키마를 만드는 테스트에는
     * 없어서, 운영에서만 되는 코드가 된다.</p>
     */
    void deleteByStudyGroup_Id(Long groupId);

    boolean existsByStudyGroup_IdAndMember_Id(Long groupId, Long memberId);

    long countByMember_Id(Long memberId);

    /**
     * 나와 같은 그룹에 속한 회원 id (나 자신 포함).
     * 그룹 공개 플랜의 노출 대상과 알림 fanout 대상이 모두 이 집합이다.
     */
    @Query("""
            select distinct other.member.id
            from GroupMember mine, GroupMember other
            where mine.studyGroup = other.studyGroup and mine.member.id = :memberId
            """)
    List<Long> findFellowMemberIds(@Param("memberId") Long memberId);

    /** 두 회원이 함께 속한 그룹 수 - 그룹 공개 플랜의 열람 자격 판정에 쓴다 */
    @Query("""
            select count(mine.id)
            from GroupMember mine, GroupMember other
            where mine.studyGroup = other.studyGroup
              and mine.member.id = :viewerId and other.member.id = :authorId
            """)
    long countSharedGroups(@Param("viewerId") Long viewerId, @Param("authorId") Long authorId);
}
