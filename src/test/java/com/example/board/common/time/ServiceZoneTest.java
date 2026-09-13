package com.example.board.common.time;

import com.example.board.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

/**
 * "오늘" 의 기준 시간대.
 *
 * <p>예전에는 {@code Clock.systemDefaultZone()} 이었다 - 즉 기계의 시간대를 따랐다.
 * 개발용 맥은 KST 지만 도커·클라우드는 대개 UTC 라, 배포하는 순간 <b>"오늘" 이 오전 9시에 바뀐다.</b>
 * 이 테스트는 그 기준이 기계가 아니라 코드에 있다는 것을 못 박는다.</p>
 */
@SpringBootTest
class ServiceZoneTest {

    @Autowired Clock clock;

    @Test
    @DisplayName("주입되는 시계는 서비스 시간대를 따른다 - 기계의 기본 시간대가 아니다")
    void clockUsesServiceZone() {
        assertThat(clock.getZone()).isEqualTo(ServiceZone.ZONE);
    }

    @Test
    @DisplayName("cron 에 쓰는 문자열과 ZoneId 는 같은 곳에서 나온다")
    void idAndZoneAgree() {
        assertThat(ServiceZone.ZONE).isEqualTo(ZoneId.of(ServiceZone.ID));
        assertThat(ServiceZone.ID).isEqualTo("Asia/Seoul");
    }

    /** 슬라이스 테스트와 통합 테스트가 서로 다른 '오늘' 을 보면 안 된다 */
    @Test
    @DisplayName("테스트용 시계도 같은 시간대다")
    void testClockAgrees() {
        assertThat(new TestClockConfig().clock().getZone()).isEqualTo(ServiceZone.ZONE);
    }
}
