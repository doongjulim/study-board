package com.example.board.auth.service;

import com.example.board.auth.domain.LoginAttempts;
import com.example.board.auth.exception.TooManyLoginAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 무차별 대입을 늦춘다.
 *
 * <p><b>왜 아이디가 아니라 요청 출처(IP)로 세는가.</b> 아이디로 잠그면 남의 아이디를 아는 사람이
 * 일부러 다섯 번 틀려서 그 사람을 로그인하지 못하게 만들 수 있다 - 공격을 막으려다
 * 더 쉬운 공격을 열어 주는 셈이다. 출처로 세면 공유 IP(회사·학교) 뒤의 사람들이
 * 실패를 나눠 갖는 문제가 있지만, 특정인을 겨냥해 잠글 수는 없다.
 * 그래서 임계값을 오타 몇 번으로는 닿지 않을 만큼 넉넉히 둔다.</p>
 *
 * <p>저장소는 메모리다. 서버가 여러 대면 대수만큼 시도가 허용되고 재시작하면 초기화되지만,
 * 무차별 대입을 <b>느리게 만드는 것</b>이 목적이라 그 정도로도 값을 한다.
 * 정확한 공유 카운터가 필요해지면 Redis 로 옮길 자리이기도 하다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    /** 오타로는 닿지 않고, 흔한 비밀번호 목록을 훑기에는 턱없이 모자란 횟수 */
    static final int THRESHOLD = 10;

    /** 이 시간 안에 임계값만큼 실패하면 막는다 */
    static final Duration WINDOW = Duration.ofMinutes(10);

    /** 막아 두는 시간 */
    static final Duration BLOCK = Duration.ofMinutes(15);

    /** 기록이 무한정 쌓이지 않도록 하는 상한 - 넘으면 지울 수 있는 것부터 비운다 */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, LoginAttempts> attemptsByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    /** 로그인 처리 전에 부른다. 막혀 있으면 비밀번호를 확인조차 하지 않는다 */
    public void checkNotBlocked(String key) {
        LoginAttempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (attempts.isBlocked(now)) {
            throw new TooManyLoginAttemptsException(attempts.retryAfterSeconds(now));
        }
    }

    public void recordFailure(String key) {
        LocalDateTime now = LocalDateTime.now(clock);
        // compute 로 읽고-고치고-쓰기를 한 번에 - 동시에 들어온 실패가 서로를 덮어쓰지 않는다
        LoginAttempts updated = attemptsByKey.compute(key, (ignored, existing) ->
                (existing != null ? existing : LoginAttempts.none())
                        .fail(now, THRESHOLD, WINDOW, BLOCK));

        if (updated.isBlocked(now)) {
            log.warn("로그인 시도 제한: key={} 실패 {}회, {}까지 차단", key, updated.failures(),
                    updated.blockedUntil());
        }
        evictExpiredIfCrowded(now);
    }

    /** 성공하면 기록을 지운다 - 어제 몇 번 틀린 것이 오늘의 발목을 잡지 않게 한다 */
    public void recordSuccess(String key) {
        attemptsByKey.remove(key);
    }

    /**
     * 상한을 넘었을 때만 훑는다. 매 요청마다 전체를 훑으면 정상 트래픽에 비용을 물리는 셈이라,
     * 실제로 커졌을 때만 비운다.
     */
    private void evictExpiredIfCrowded(LocalDateTime now) {
        if (attemptsByKey.size() <= MAX_TRACKED) {
            return;
        }
        attemptsByKey.entrySet()
                .removeIf(entry -> entry.getValue().isExpired(now, WINDOW));
    }
}
