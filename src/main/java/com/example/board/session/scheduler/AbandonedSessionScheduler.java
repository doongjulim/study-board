package com.example.board.session.scheduler;

import com.example.board.session.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 켜둔 채 잊어버린 학습 세션을 주기적으로 닫는다.
 *
 * <p>타이머를 켜고 그대로 잠들면 다음 날까지 "진행 중" 으로 남아 통계가 망가지고,
 * 진행 중 세션이 하나로 제한되므로 새 학습도 시작할 수 없게 된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedSessionScheduler {

    private final StudySessionService studySessionService;
    private final Clock clock;

    /** 10분마다 확인한다 - 최대 길이(6시간)에 비하면 충분히 촘촘하다 */
    @Scheduled(fixedRate = 600_000)
    public void closeAbandonedSessions() {
        int closed = studySessionService.closeAbandoned(LocalDateTime.now(clock));
        if (closed > 0) {
            log.info("방치된 학습 세션 {}건을 자동 종료했습니다.", closed);
        }
    }
}
