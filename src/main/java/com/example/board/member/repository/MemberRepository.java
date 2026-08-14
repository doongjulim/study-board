package com.example.board.member.repository;

import com.example.board.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    /** 비밀번호 찾기 - 이메일로 계정을 찾는다 */
    Optional<Member> findByEmail(String email);

    /**
     * 플랜 공유 알림을 받기로 한 활성 회원 id.
     * 사람 수만큼 회원을 하나씩 불러 설정을 확인하지 않도록 조회 단계에서 거른다.
     */
    @Query("""
            select m.id from Member m
            where m.withdrawnAt is null
              and m.notificationPreference.planSharedEnabled = true
            """)
    List<Long> findIdsAllowingPlanSharedNotification();

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);
}
