package com.example.board.auth.scheduler;

import com.example.board.auth.service.PasswordResetService;
import com.example.board.auth.service.RefreshTokenService;
import com.example.board.member.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 만료된 토큰(리프레시·비밀번호 재설정)을 매일 새벽에 정리한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *")
    public void deleteExpiredRefreshTokens() {
        int deleted = refreshTokenService.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건을 정리했습니다.", deleted);
        }
    }

    @Scheduled(cron = "0 10 4 * * *")
    public void deleteExpiredPasswordResetTokens() {
        int deleted = passwordResetService.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("만료된 비밀번호 재설정 토큰 {}건을 정리했습니다.", deleted);
        }
    }

    @Scheduled(cron = "0 15 4 * * *")
    public void deleteExpiredEmailVerificationTokens() {
        int deleted = emailVerificationService.deleteExpired(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("만료된 이메일 인증 토큰 {}건을 정리했습니다.", deleted);
        }
    }
}
