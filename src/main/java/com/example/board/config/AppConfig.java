package com.example.board.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.board.common.time.ServiceZone;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
@EnableScheduling
public class AppConfig {

    /**
     * 현재 시각을 주입 가능한 형태로 노출한다.
     *
     * <p>학습 타이머처럼 "지금 몇 시인가" 가 결과를 좌우하는 기능은
     * {@code LocalDateTime.now()} 를 직접 부르면 테스트에서 시각을 고정할 수 없다.</p>
     *
     * <p>시간대는 {@link ServiceZone} 이 정한다 - 기계의 기본 시간대를 따르면
     * 로컬(KST)과 배포 환경(대개 UTC)에서 "오늘" 이 서로 다른 날이 된다.</p>
     */
    @Bean
    public Clock clock() {
        return Clock.system(ServiceZone.ZONE);
    }
}
