package com.example.board.auth.repository;

import com.example.board.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 갱신 시 회원 정보로 새 액세스 토큰을 만들어야 하므로 member 를 함께 로딩한다 */
    @EntityGraph(attributePaths = "member")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    /** 회원의 모든 기기 로그아웃 - 비밀번호 변경·탈퇴 시 사용 */
    void deleteByMember_Id(Long memberId);

    /** 만료된 토큰 정리용 */
    int deleteByExpiresAtBefore(LocalDateTime time);
}
