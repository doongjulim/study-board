package com.example.board.stats.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StatsPeriodTest {

    @Test
    @DisplayName("주간은 어느 요일을 넣어도 그 주 월요일~일요일이다")
    void week() {
        StatsPeriod period = StatsPeriod.week(LocalDate.of(2026, 9, 17)); // 목요일

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("월간은 어느 날을 넣어도 그 달 1일~말일이다")
    void month() {
        StatsPeriod period = StatsPeriod.month(LocalDate.of(2026, 2, 17));

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 2, 28)); // 말일은 달마다 다르다
    }

    @Test
    @DisplayName("지난 기간은 같은 종류의 바로 앞 기간이다 - 주는 한 주 앞, 달은 한 달 앞")
    void previous() {
        assertThat(StatsPeriod.week(LocalDate.of(2026, 9, 17)).previous().from())
                .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(StatsPeriod.month(LocalDate.of(2026, 3, 31)).previous())
                .isEqualTo(new StatsPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), true));
    }

    @Test
    @DisplayName("3월 31일의 다음 달은 4월 1일~30일이다 - 날짜를 그대로 더하면 5월 1일이 된다")
    void next_monthEndDoesNotSkip() {
        assertThat(StatsPeriod.month(LocalDate.of(2026, 3, 31)).next())
                .isEqualTo(new StatsPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), true));
    }

    @Test
    @DisplayName("모르는 기간 문자열은 주간으로 다룬다 - 주소를 손으로 고쳐도 화면이 뜬다")
    void of_unknownFallsBackToWeek() {
        assertThat(StatsPeriod.of("quarter", LocalDate.of(2026, 9, 17)).monthly()).isFalse();
        assertThat(StatsPeriod.of(null, LocalDate.of(2026, 9, 17)).monthly()).isFalse();
        assertThat(StatsPeriod.of("month", LocalDate.of(2026, 9, 17)).monthly()).isTrue();
    }

    @Test
    @DisplayName("화면에 쓰는 이름은 기간이 정한다 - 문구가 화면마다 갈리지 않게")
    void labels() {
        StatsPeriod week = StatsPeriod.week(LocalDate.of(2026, 9, 17));
        assertThat(week.key()).isEqualTo("week");
        assertThat(week.label()).isEqualTo("이번 주");
        assertThat(week.previousLabel()).isEqualTo("지난주");

        StatsPeriod month = StatsPeriod.month(LocalDate.of(2026, 9, 17));
        assertThat(month.key()).isEqualTo("month");
        assertThat(month.label()).isEqualTo("이번 달");
        assertThat(month.previousLabel()).isEqualTo("지난달");
    }
}
