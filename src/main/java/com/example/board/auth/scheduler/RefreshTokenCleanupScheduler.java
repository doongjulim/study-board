package com.example.board.auth.scheduler;

import com.example.board.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 만료된 리프레시 토큰이 쌓이지 않도록 매일 새벽에 정리한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "0 0 4 * * *")
    public void deleteExpiredTokens() {
        int deleted = refreshTokenService.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건을 정리했습니다.", deleted);
        }
    }
}
