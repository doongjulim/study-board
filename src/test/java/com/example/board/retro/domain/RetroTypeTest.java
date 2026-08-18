package com.example.board.retro.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("회고 주기")
class RetroTypeTest {

    @Test
    @DisplayName("주간 회고는 어느 요일에 써도 그 주 월요일에 매달린다 - 한 주에 일곱 개가 생기지 않게")
    void weeklyAnchorsToMonday() {
        LocalDate wednesday = LocalDate.of(2026, 8, 12);
        LocalDate sunday = LocalDate.of(2026, 8, 16);
        LocalDate monday = LocalDate.of(2026, 8, 10);

        assertThat(RetroType.WEEKLY.anchorDate(wednesday)).isEqualTo(monday);
        assertThat(RetroType.WEEKLY.anchorDate(sunday)).isEqualTo(monday);
        assertThat(RetroType.WEEKLY.anchorDate(monday)).isEqualTo(monday);
    }

    @Test
    @DisplayName("하루 회고는 쓴 날짜 그대로 매달린다")
    void dailyKeepsItsDate() {
        LocalDate wednesday = LocalDate.of(2026, 8, 12);

        assertThat(RetroType.DAILY.anchorDate(wednesday)).isEqualTo(wednesday);
    }

    @Test
    @DisplayName("하루 회고는 한 줄만 받는다 - 길게 쓰라고 하면 아무도 쓰지 않는다")
    void dailyIsShort() {
        assertThat(RetroType.DAILY.getMaxLength()).isEqualTo(200);
        assertThat(RetroType.DAILY.fits("a".repeat(200))).isTrue();
        assertThat(RetroType.DAILY.fits("a".repeat(201))).isFalse();
    }

    @Test
    @DisplayName("주간 회고는 넉넉히 받는다")
    void weeklyIsLonger() {
        assertThat(RetroType.WEEKLY.fits("a".repeat(2000))).isTrue();
        assertThat(RetroType.WEEKLY.fits("a".repeat(2001))).isFalse();
    }

    @Test
    @DisplayName("빈 회고는 회고가 아니다")
    void blankIsNotAccepted() {
        assertThat(RetroType.DAILY.fits(null)).isFalse();
        assertThat(RetroType.DAILY.fits("")).isFalse();
        assertThat(RetroType.DAILY.fits("   ")).isFalse();
    }

    @Test
    @DisplayName("앞뒤 공백은 길이에서 빼고 센다 - 줄바꿈 때문에 거절당하지 않게")
    void trimsBeforeMeasuring() {
        assertThat(RetroType.DAILY.fits("  " + "a".repeat(200) + "\n")).isTrue();
    }
}
