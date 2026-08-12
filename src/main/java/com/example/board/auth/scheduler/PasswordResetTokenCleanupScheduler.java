package com.example.board.auth.scheduler;

import com.example.board.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 만료된 비밀번호 재설정 토큰을 매일 새벽에 정리한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCleanupScheduler {

    private final PasswordResetService passwordResetService;
    private final Clock clock;

    @Scheduled(cron = "0 10 4 * * *")
    public void deleteExpiredTokens() {
        int deleted = passwordResetService.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("만료된 비밀번호 재설정 토큰 {}건을 정리했습니다.", deleted);
        }
    }
}
