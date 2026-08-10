package com.example.board.session.scheduler;

import com.example.board.session.service.StudySessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbandonedSessionScheduler")
class AbandonedSessionSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 15, 0);

    @Mock StudySessionService studySessionService;

    private AbandonedSessionScheduler scheduler() {
        Clock fixed = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new AbandonedSessionScheduler(studySessionService, fixed);
    }

    @Test
    @DisplayName("고정된 현재 시각을 그대로 서비스에 넘겨 방치 세션을 정리한다")
    void delegatesWithCurrentTime() {
        given(studySessionService.closeAbandoned(NOW)).willReturn(2);

        scheduler().closeAbandonedSessions();

        then(studySessionService).should().closeAbandoned(NOW);
    }

    @Test
    @DisplayName("정리할 세션이 없어도 예외 없이 지나간다")
    void nothingToClose() {
        given(studySessionService.closeAbandoned(NOW)).willReturn(0);

        scheduler().closeAbandonedSessions();

        then(studySessionService).should().closeAbandoned(NOW);
    }
}
