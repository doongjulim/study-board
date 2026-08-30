package com.example.board.notification.scheduler;

import com.example.board.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 오래된 알림을 매일 새벽에 정리한다.
 *
 * <p>알림은 한 번 만들어지면 아무도 지우지 않았다. 탈퇴할 때만 사라지므로,
 * 오래 쓰는 회원의 알림 행은 끝없이 쌓인다. 전체 공개 플랜 하나가 회원 수만큼 행을 만드니
 * 더 빨리 쌓인다 - 지우는 규칙이 있어야 기능이 완성된다.</p>
 *
 * <p>보존 기간을 여기에 상수로 두는 이유: "언제부터 사라지는가" 는 정책이고,
 * 정책은 그것을 실행하는 자리에 적혀 있어야 나중에 찾을 수 있다.
 * 토큰 정리와 같은 새벽 시간대에 붙여, 정리 작업이 한 시간대에 모이게 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    /** 읽은 알림은 석 달이면 다시 볼 일이 없다 */
    private static final int READ_RETENTION_DAYS = 90;
    /** 안 읽었더라도 반년이 지나면 알림으로서의 쓸모가 없다 */
    private static final int UNREAD_RETENTION_DAYS = 180;

    private final NotificationService notificationService;
    private final Clock clock;

    @Scheduled(cron = "0 20 4 * * *")
    public void deleteOldNotifications() {
        LocalDateTime now = LocalDateTime.now(clock);
        int deleted = notificationService.deleteOld(
                now.minusDays(READ_RETENTION_DAYS), now.minusDays(UNREAD_RETENTION_DAYS));
        if (deleted > 0) {
            log.info("오래된 알림 {}건을 정리했습니다.", deleted);
        }
    }
}
