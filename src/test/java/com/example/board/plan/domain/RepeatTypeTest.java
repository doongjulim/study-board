package com.example.board.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatTypeTest {

    /** 2026-08-03 은 월요일 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);

    @Test
    @DisplayName("NONE 은 종료일과 무관하게 시작일 하루만 만든다")
    void none() {
        List<LocalDate> dates = RepeatType.NONE.datesBetween(MONDAY, MONDAY.plusDays(30));

        assertThat(dates).containsExactly(MONDAY);
    }

    @Test
    @DisplayName("DAILY 는 종료일까지 하루 간격으로 만든다")
    void daily() {
        List<LocalDate> dates = RepeatType.DAILY.datesBetween(MONDAY, MONDAY.plusDays(3));

        assertThat(dates).containsExactly(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3));
    }

    @Test
    @DisplayName("WEEKDAY 는 주말을 건너뛴다")
    void weekday() {
        List<LocalDate> dates = RepeatType.WEEKDAY.datesBetween(MONDAY, MONDAY.plusDays(8));

        assertThat(dates).containsExactly(
                MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4),
                MONDAY.plusDays(7), MONDAY.plusDays(8)); // 토(5)·일(6) 제외
    }

    @Test
    @DisplayName("WEEKLY 는 시작일과 같은 요일로 7일 간격으로 만든다")
    void weekly() {
        List<LocalDate> dates = RepeatType.WEEKLY.datesBetween(MONDAY, MONDAY.plusDays(20));

        assertThat(dates).containsExactly(MONDAY, MONDAY.plusDays(7), MONDAY.plusDays(14));
        assertThat(dates).allMatch(date -> date.getDayOfWeek() == MONDAY.getDayOfWeek());
    }

    @Test
    @DisplayName("종료일이 없거나 시작일보다 빠르면 시작일 하루만 만든다")
    void invalidUntil() {
        assertThat(RepeatType.DAILY.datesBetween(MONDAY, null)).containsExactly(MONDAY);
        assertThat(RepeatType.DAILY.datesBetween(MONDAY, MONDAY.minusDays(1))).containsExactly(MONDAY);
    }

    @Test
    @DisplayName("한 번에 만드는 개수는 상한을 넘지 않는다")
    void respectsLimit() {
        List<LocalDate> dates = RepeatType.DAILY.datesBetween(MONDAY, MONDAY.plusYears(3));

        assertThat(dates).hasSize(RepeatType.MAX_OCCURRENCES);
        assertThat(RepeatType.DAILY.exceedsLimit(MONDAY, MONDAY.plusYears(3))).isTrue();
        assertThat(RepeatType.DAILY.exceedsLimit(MONDAY, MONDAY.plusDays(6))).isFalse();
        assertThat(RepeatType.NONE.exceedsLimit(MONDAY, MONDAY.plusYears(3))).isFalse();
    }
}
