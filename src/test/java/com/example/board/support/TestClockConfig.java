package com.example.board.support;

import com.example.board.common.time.ServiceZone;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 컨트롤러 슬라이스에 {@link Clock} 을 넣어 준다.
 *
 * <p>{@code @WebMvcTest} 는 컨트롤러만 올리므로 {@code AppConfig} 의 Clock 빈이 없다.
 * {@code AppConfig} 를 통째로 import 하지 않는 이유는 거기에 스케줄링·JPA 감사도 함께 붙어 있어서다 -
 * 슬라이스가 알아야 할 것보다 많아진다.</p>
 *
 * <p>고정 시각이 아니라 <b>운영과 같은 시계</b>를 준다. 특정 날짜에 묶으면 "오늘" 을 쓰는 화면들의
 * 기대값을 전부 다시 계산해야 하고, 이 테스트들이 확인하려는 것은 날짜 계산이 아니라 라우팅·권한이다.
 * 시각 자체가 논점인 테스트는 각자 고정 Clock 을 세운다(예: {@code EmailVerificationServiceTest}).</p>
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ServiceZone.ZONE);
    }
}
