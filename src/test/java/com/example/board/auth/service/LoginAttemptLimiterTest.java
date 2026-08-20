package com.example.board.auth.service;

import com.example.board.auth.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

@DisplayName("로그인 시도 제한")
class LoginAttemptLimiterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** 시간을 앞으로 밀 수 있는 시계 - 막힘이 풀리는지 보려면 필요하다 */
    private static class MovableClock extends Clock {
        private Instant instant;

        MovableClock(LocalDateTime at) {
            this.instant = at.atZone(ZONE).toInstant();
        }

        void forwardMinutes(long minutes) {
            instant = instant.plusSeconds(minutes * 60);
        }

        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private final MovableClock clock = new MovableClock(NOW);
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void failTimes(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(key);
        }
    }

    @Test
    @DisplayName("한 번도 실패하지 않은 출처는 그냥 통과한다")
    void unknownKeyPasses() {
        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"));
    }

    @Test
    @DisplayName("임계값 전까지는 통과한다 - 오타 몇 번으로 막히면 안 된다")
    void belowThresholdPasses() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD - 1);

        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"));
    }

    @Test
    @DisplayName("임계값을 넘기면 막고, 남은 시간을 함께 알려 준다")
    void blocksAfterThreshold() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD);

        assertThatThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessageContaining("다시 시도");
    }

    @Test
    @DisplayName("막는 것은 그 출처뿐이다 - 다른 곳에서 들어온 사람은 영향받지 않는다")
    void blocksOnlyThatKey() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD);

        assertThatThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("2.2.2.2"));
    }

    @Test
    @DisplayName("차단 시간이 지나면 저절로 풀린다")
    void unblocksAfterBlockDuration() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD);

        clock.forwardMinutes(LoginAttemptLimiter.BLOCK.toMinutes());

        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"));
    }

    @Test
    @DisplayName("창이 지나면 실패 횟수를 처음부터 다시 센다")
    void countResetsAfterWindow() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD - 1);

        clock.forwardMinutes(LoginAttemptLimiter.WINDOW.toMinutes());
        limiter.recordFailure("1.1.1.1");

        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"));
    }

    @Test
    @DisplayName("로그인에 성공하면 기록이 사라진다 - 어제 틀린 것이 오늘의 발목을 잡지 않게")
    void successClearsHistory() {
        failTimes("1.1.1.1", LoginAttemptLimiter.THRESHOLD - 1);

        limiter.recordSuccess("1.1.1.1");
        limiter.recordFailure("1.1.1.1");

        assertThatNoException().isThrownBy(() -> limiter.checkNotBlocked("1.1.1.1"));
    }
}
