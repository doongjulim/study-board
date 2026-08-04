package com.example.board.member.repository;

import com.example.board.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    /** 공지형 알림 발송 대상 - 전체 회원 id 만 조회한다 */
    @Query("select m.id from Member m")
    List<Long> findAllIds();

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);
}
