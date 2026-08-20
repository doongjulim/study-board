package com.example.board.auth.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 한 출처에서 이어진 로그인 실패 기록.
 *
 * <p>비밀번호는 BCrypt 로 지켜지지만, 그것은 <b>DB 가 새어 나갔을 때</b>의 방어다.
 * 로그인 창에 대고 계속 찔러 보는 공격은 아무것도 막지 않는다 -
 * 흔한 비밀번호 몇백 개를 순서대로 넣어 보면 언젠가 하나는 맞는다.</p>
 *
 * <p>시간과 횟수만 다루는 값 객체라 DB 도 스레드도 없이 검증된다.
 * 실제 저장과 동시성은 {@code LoginAttemptLimiter} 가 맡는다.</p>
 *
 * @param failures    연속 실패 횟수
 * @param firstFailureAt 이 연속 실패가 시작된 시각 (창이 지나면 처음부터 다시 센다)
 * @param blockedUntil   막혀 있는 끝 시각. null 이면 막히지 않은 상태다
 */
public record LoginAttempts(int failures, LocalDateTime firstFailureAt, LocalDateTime blockedUntil) {

    public static LoginAttempts none() {
        return new LoginAttempts(0, null, null);
    }

    /**
     * 실패를 한 번 기록한다.
     *
     * <p>창(window)을 두는 이유는, 며칠에 걸쳐 이따금 틀린 사람과 방금 열 번 찌른 공격을
     * 같게 취급하면 안 되기 때문이다. 마지막 실패로부터가 아니라 <b>처음 실패로부터</b> 창을 재므로,
     * 창 안에서 계속 찌르는 동안 창이 뒤로 밀리지 않는다.</p>
     */
    public LoginAttempts fail(LocalDateTime now, int threshold, Duration window, Duration block) {
        if (isBlocked(now)) {
            return this; // 이미 막힌 동안의 시도는 형량을 늘리지 않는다 (무한정 잠기는 것을 막는다)
        }
        boolean windowExpired = firstFailureAt == null
                || !now.isBefore(firstFailureAt.plus(window));
        int next = windowExpired ? 1 : failures + 1;
        LocalDateTime startedAt = windowExpired ? now : firstFailureAt;

        if (next >= threshold) {
            return new LoginAttempts(next, startedAt, now.plus(block));
        }
        return new LoginAttempts(next, startedAt, null);
    }

    public boolean isBlocked(LocalDateTime now) {
        return blockedUntil != null && now.isBefore(blockedUntil);
    }

    /** 안내 문구에 쓸 남은 시간(초). 막혀 있지 않으면 0 */
    public long retryAfterSeconds(LocalDateTime now) {
        if (!isBlocked(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, blockedUntil).toSeconds());
    }

    /**
     * 더 들고 있을 이유가 없는 기록인지.
     * 막힘이 풀렸고 창도 지났다면 지워도 되는 값이라, 메모리가 무한정 늘지 않게 한다.
     */
    public boolean isExpired(LocalDateTime now, Duration window) {
        if (isBlocked(now)) {
            return false;
        }
        return firstFailureAt == null || !now.isBefore(firstFailureAt.plus(window));
    }
}
