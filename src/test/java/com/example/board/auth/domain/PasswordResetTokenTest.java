package com.example.board.auth.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordResetToken")
class PasswordResetTokenTest {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 8, 12, 10, 0);

    private final Member member = new Member("tester1", "encoded-password", "테스터", "me@example.com");

    private PasswordResetToken token() {
        return new PasswordResetToken(member, "hashed-token", ISSUED_AT);
    }

    @Test
    @DisplayName("발급 시각으로부터 유효 시간만큼 살아 있다")
    void expiresAfterValidity() {
        assertThat(token().getExpiresAt())
                .isEqualTo(ISSUED_AT.plus(PasswordResetToken.VALIDITY));
    }

    @Test
    @DisplayName("유효 시간 안에는 만료되지 않는다")
    void notExpiredWithinValidity() {
        assertThat(token().isExpired(ISSUED_AT.plusMinutes(29))).isFalse();
    }

    @Test
    @DisplayName("유효 시간이 지나면 만료된다")
    void expiredAfterValidity() {
        assertThat(token().isExpired(ISSUED_AT.plusMinutes(31))).isTrue();
    }

    @Test
    @DisplayName("원문이 아니라 해시만 보관한다")
    void storesHashOnly() {
        assertThat(token().getTokenHash()).isEqualTo("hashed-token");
    }
}
