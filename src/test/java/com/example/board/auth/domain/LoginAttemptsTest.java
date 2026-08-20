package com.example.board.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("로그인 실패 기록")
class LoginAttemptsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final int THRESHOLD = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK = Duration.ofMinutes(15);

    private LoginAttempts failTimes(int times, LocalDateTime at) {
        LoginAttempts attempts = LoginAttempts.none();
        for (int i = 0; i < times; i++) {
            attempts = attempts.fail(at, THRESHOLD, WINDOW, BLOCK);
        }
        return attempts;
    }

    @Test
    @DisplayName("임계값 전까지는 막지 않는다 - 오타 몇 번으로 잠기면 안 된다")
    void belowThresholdIsNotBlocked() {
        LoginAttempts attempts = failTimes(THRESHOLD - 1, NOW);

        assertThat(attempts.failures()).isEqualTo(THRESHOLD - 1);
        assertThat(attempts.isBlocked(NOW)).isFalse();
    }

    @Test
    @DisplayName("임계값에 닿으면 정해진 시간 동안 막는다")
    void reachingThresholdBlocks() {
        LoginAttempts attempts = failTimes(THRESHOLD, NOW);

        assertThat(attempts.isBlocked(NOW)).isTrue();
        assertThat(attempts.blockedUntil()).isEqualTo(NOW.plus(BLOCK));
    }

    @Test
    @DisplayName("막힘은 시간이 지나면 저절로 풀린다")
    void blockExpires() {
        LoginAttempts attempts = failTimes(THRESHOLD, NOW);

        assertThat(attempts.isBlocked(NOW.plus(BLOCK).minusSeconds(1))).isTrue();
        assertThat(attempts.isBlocked(NOW.plus(BLOCK))).isFalse();
    }

    @Test
    @DisplayName("창이 지난 뒤의 실패는 처음부터 다시 센다 - 며칠에 한 번 틀리는 사람은 공격이 아니다")
    void windowResetsCount() {
        LoginAttempts attempts = failTimes(THRESHOLD - 1, NOW);

        LoginAttempts later = attempts.fail(NOW.plus(WINDOW), THRESHOLD, WINDOW, BLOCK);

        assertThat(later.failures()).isEqualTo(1);
        assertThat(later.isBlocked(NOW.plus(WINDOW))).isFalse();
    }

    @Test
    @DisplayName("창은 처음 실패로부터 잰다 - 계속 찌르는 동안 창이 뒤로 밀리면 영원히 안 막힌다")
    void windowIsMeasuredFromFirstFailure() {
        LoginAttempts attempts = LoginAttempts.none();
        // 창(10분) 안에서 3분 간격으로 다섯 번
        for (int i = 0; i < THRESHOLD; i++) {
            attempts = attempts.fail(NOW.plusMinutes(i * 2L), THRESHOLD, WINDOW, BLOCK);
        }

        assertThat(attempts.failures()).isEqualTo(THRESHOLD);
        assertThat(attempts.isBlocked(NOW.plusMinutes(8))).isTrue();
    }

    @Test
    @DisplayName("막혀 있는 동안의 시도는 형량을 늘리지 않는다 - 계속 두드린다고 무한정 잠기면 안 된다")
    void attemptsWhileBlockedDoNotExtendIt() {
        LoginAttempts blocked = failTimes(THRESHOLD, NOW);

        LoginAttempts again = blocked.fail(NOW.plusMinutes(1), THRESHOLD, WINDOW, BLOCK);

        assertThat(again.blockedUntil()).isEqualTo(blocked.blockedUntil());
    }

    @Test
    @DisplayName("남은 시간을 초로 알려 준다 (안내 문구용)")
    void tellsRetryAfter() {
        LoginAttempts attempts = failTimes(THRESHOLD, NOW);

        assertThat(attempts.retryAfterSeconds(NOW)).isEqualTo(BLOCK.toSeconds());
        assertThat(attempts.retryAfterSeconds(NOW.plus(BLOCK))).isZero();
    }

    @Test
    @DisplayName("막힘이 풀리고 창도 지난 기록은 버려도 된다 - 메모리가 무한정 늘지 않게")
    void expiredRecordCanBeDropped() {
        LoginAttempts attempts = failTimes(THRESHOLD, NOW);

        assertThat(attempts.isExpired(NOW, WINDOW)).isFalse();                  // 아직 막혀 있다
        assertThat(attempts.isExpired(NOW.plus(BLOCK), WINDOW)).isTrue();       // 풀렸고 창도 지났다
    }

    @Test
    @DisplayName("한 번도 실패하지 않은 기록은 처음부터 버릴 수 있다")
    void freshRecordIsExpired() {
        assertThat(LoginAttempts.none().isExpired(NOW, WINDOW)).isTrue();
        assertThat(LoginAttempts.none().isBlocked(NOW)).isFalse();
    }
}
