package com.example.board.member.repository;

import com.example.board.member.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /** 해시로만 찾는다 - 원문은 메일에만 있고 우리는 갖고 있지 않다 */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** 새 링크를 내면 이전 링크는 무효가 되어야 한다 */
    void deleteByMember_Id(Long memberId);

    @Modifying(clearAutomatically = true)
    @Query("delete from EmailVerificationToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
