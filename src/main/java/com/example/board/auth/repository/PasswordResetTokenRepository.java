package com.example.board.auth.repository;

import com.example.board.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** 재설정 대상 회원을 함께 로딩한다 (open-in-view 가 꺼져 있으므로) */
    @EntityGraph(attributePaths = "member")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 링크를 보내면 이전 링크는 무효가 되어야 한다 */
    void deleteByMember_Id(Long memberId);

    int deleteByExpiresAtBefore(LocalDateTime time);
}
