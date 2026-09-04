package com.example.board.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이메일 인증 토큰.
 *
 * <p>비밀번호 재설정 토큰과 같은 규칙이다 - 원문은 메일로만 나가고 DB 에는 SHA-256 해시만 남으며,
 * 한 번 쓰면 폐기된다. 다른 점은 유효 시간(24시간)과, <b>발급 당시의 주소를 함께 든다</b>는 것이다.</p>
 *
 * <p>주소를 함께 드는 이유: 메일을 보낸 뒤 사용자가 주소를 또 바꿀 수 있다. 그때 옛 링크를 누르면
 * 지금 쓰지도 않는 주소가 인증된 것으로 표시된다. 링크가 가리키는 주소와 현재 주소가 다르면 거절한다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EmailVerificationToken {

    /** 인증 메일은 급히 누를 이유가 없다. 메일함을 하루 뒤에 여는 일은 흔하다 */
    public static final Duration VALIDITY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 발급 당시의 주소 - 그 사이 주소가 바뀌었으면 이 링크는 더 이상 맞지 않는다 */
    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public EmailVerificationToken(Member member, String email, String tokenHash, LocalDateTime issuedAt) {
        this.member = member;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = issuedAt.plus(VALIDITY);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    /** 발급 이후 주소가 바뀌지 않았는가 */
    public boolean matches(String currentEmail) {
        return email.equalsIgnoreCase(currentEmail);
    }
}
