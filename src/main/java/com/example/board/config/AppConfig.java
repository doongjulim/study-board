package com.example.board.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
@EnableScheduling
public class AppConfig {

    /**
     * 현재 시각을 주입 가능한 형태로 노출한다.
     * 학습 타이머처럼 "지금 몇 시인가" 가 결과를 좌우하는 기능은
     * LocalDateTime.now() 를 직접 부르면 테스트에서 시각을 고정할 수 없다.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
