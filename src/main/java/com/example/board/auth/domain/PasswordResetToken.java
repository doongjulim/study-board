package com.example.board.auth.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 토큰.
 *
 * <p>리프레시 토큰과 마찬가지로 원문은 메일로만 나가고 DB 에는 SHA-256 해시만 남긴다.
 * 메일함이 오래 열려 있을 수 있으므로 유효 시간을 짧게 두고, 한 번 쓰면 즉시 폐기한다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken {

    /** 메일을 확인하고 새 비밀번호를 정하기에 충분하면서, 방치된 링크가 오래 살아 있지 않을 시간 */
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public PasswordResetToken(Member member, String tokenHash, LocalDateTime issuedAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = issuedAt.plus(VALIDITY);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
