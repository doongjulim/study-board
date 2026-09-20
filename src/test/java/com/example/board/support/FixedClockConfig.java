package com.example.board.support;

import com.example.board.common.time.ServiceZone;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.LocalDate;

/**
 * "오늘" 을 한 날짜에 묶은 시계.
 *
 * <p>{@link TestClockConfig} 와 나뉘어 있는 이유는 쓰임이 다르기 때문이다 -
 * 저쪽은 <b>라우팅·권한</b>을 보는 테스트가 Clock 빈을 얻기 위한 것이라 운영과 같은 시계를 주고,
 * 이쪽은 화면에 <b>날짜가 그려지는지</b>를 보는 테스트가 쓴다. D-3 을 확인하려면 오늘이 고정되어야 한다.
 *
 * <p>공통으로 뺀 이유: 똑같은 중첩 클래스가 테스트마다 다시 쓰이고 있었고(홈·그룹),
 * 그때마다 기준 날짜가 조금씩 달라 "이 테스트의 오늘은 언제인가" 를 매번 찾아봐야 했다.
 * 시간대는 {@link ServiceZone} 을 따른다 - 기계의 시간대를 쓰면 CI 에서만 하루가 어긋난다.
 */
@TestConfiguration
public class FixedClockConfig {

    /** 2026-08-12 은 수요일 - 주/월 경계에 걸치지 않아 기대값을 읽기 쉽다 */
    public static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @Bean
    public Clock clock() {
        return Clock.fixed(TODAY.atStartOfDay(ServiceZone.ZONE).toInstant(), ServiceZone.ZONE);
    }
}
