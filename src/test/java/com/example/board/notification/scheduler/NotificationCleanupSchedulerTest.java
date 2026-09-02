package com.example.board.notification.scheduler;

import com.example.board.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

/**
 * 보존 기간은 정책이다. 숫자가 조용히 바뀌면 사용자의 알림이 예고 없이 사라지므로,
 * "언제부터 지워지는가" 를 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationCleanupSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 4, 20);

    @Mock NotificationService notificationService;

    private NotificationCleanupScheduler scheduler() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        return new NotificationCleanupScheduler(notificationService, clock);
    }

    @Test
    @DisplayName("읽은 알림은 90일, 안 읽은 알림은 180일 기준으로 넘긴다")
    void retentionWindows() {
        scheduler().deleteOldNotifications();

        ArgumentCaptor<LocalDateTime> readBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> anyBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        then(notificationService).should().deleteOld(readBefore.capture(), anyBefore.capture());

        assertThat(readBefore.getValue()).isEqualTo(NOW.minusDays(90));
        assertThat(anyBefore.getValue()).isEqualTo(NOW.minusDays(180));
    }

    @Test
    @DisplayName("읽은 것의 보존 기간이 안 읽은 것보다 짧다 - 순서가 뒤집히면 안 읽은 알림이 먼저 사라진다")
    void readRetentionIsShorter() {
        scheduler().deleteOldNotifications();

        ArgumentCaptor<LocalDateTime> readBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> anyBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        then(notificationService).should().deleteOld(readBefore.capture(), anyBefore.capture());

        assertThat(anyBefore.getValue()).isBefore(readBefore.getValue());
    }
}
