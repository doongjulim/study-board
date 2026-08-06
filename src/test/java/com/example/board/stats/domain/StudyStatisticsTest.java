package com.example.board.stats.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyStatisticsTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(PlanCategory category, LocalDate date, LocalTime start, LocalTime end, boolean completed) {
        Plan plan = new Plan("공부", null, author, category, date, start, end);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    @Test
    @DisplayName("계획이 없으면 모든 수치가 0 이고 기간만큼의 일별 항목이 채워진다")
    void empty() {
        StudyStatistics stats = StudyStatistics.of(List.of(), MONDAY, SUNDAY);

        assertThat(stats.totalCount()).isZero();
        assertThat(stats.completionRate()).isZero();
        assertThat(stats.completedMinutes()).isZero();
        assertThat(stats.categories()).isEmpty();
        assertThat(stats.dailyTrend()).hasSize(7);
        assertThat(stats.dailyTrend()).allMatch(StudyStatistics.DailyStat::isEmpty);
    }

    @Test
    @DisplayName("완료율은 완료 개수 기준으로 반올림해 계산한다")
    void completionRate() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.CODING_TEST, MONDAY, null, null, true),
                plan(PlanCategory.CODING_TEST, MONDAY, null, null, false),
                plan(PlanCategory.MAJOR, MONDAY, null, null, false)
        ), MONDAY, SUNDAY);

        assertThat(stats.totalCount()).isEqualTo(3);
        assertThat(stats.completedCount()).isEqualTo(1);
        assertThat(stats.completionRate()).isEqualTo(33);
    }

    @Test
    @DisplayName("공부 시간은 완료한 플랜만 합산하고, 종일 일정은 0분으로 본다")
    void completedMinutesOnly() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true),   // 180분
                plan(PlanCategory.MAJOR, MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 30), false),      // 미완료 제외
                plan(PlanCategory.MAJOR, MONDAY, null, null, true)                                       // 종일 → 0분
        ), MONDAY, SUNDAY);

        assertThat(stats.completedMinutes()).isEqualTo(180);
        assertThat(stats.completedHours()).isEqualTo(3);
        assertThat(stats.remainderMinutes()).isZero();
    }

    @Test
    @DisplayName("분류별 집계는 공부 시간이 많은 순으로 정렬된다")
    void categoriesSortedByMinutes() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.RESUME, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), true),      // 60분
                plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), true) // 180분
        ), MONDAY, SUNDAY);

        assertThat(stats.categories()).extracting(StudyStatistics.CategoryStat::category)
                .containsExactly(PlanCategory.CODING_TEST, PlanCategory.RESUME);
        assertThat(stats.categories().get(0).share()).isEqualTo(75); // 180 / 240
        assertThat(stats.categories().get(1).share()).isEqualTo(25);
    }

    @Test
    @DisplayName("기록된 공부 시간이 없으면 분류 비중을 개수 기준으로 계산한다")
    void categoryShareFallsBackToCount() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.CODING_TEST, MONDAY, null, null, false),
                plan(PlanCategory.CODING_TEST, MONDAY, null, null, false),
                plan(PlanCategory.INTERVIEW, MONDAY, null, null, false)
        ), MONDAY, SUNDAY);

        assertThat(stats.categories().get(0).share()).isEqualTo(67); // 2/3
    }

    @Test
    @DisplayName("일별 추이는 계획이 없는 날도 빈 항목으로 채운다")
    void dailyTrendFillsGaps() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.MAJOR, MONDAY, null, null, true),
                plan(PlanCategory.MAJOR, MONDAY.plusDays(2), null, null, false)
        ), MONDAY, SUNDAY);

        assertThat(stats.dailyTrend()).hasSize(7);
        assertThat(stats.dailyTrend().get(0).completionRate()).isEqualTo(100);
        assertThat(stats.dailyTrend().get(1).isEmpty()).isTrue();
        assertThat(stats.dailyTrend().get(2).completionRate()).isZero();
    }

    @Test
    @DisplayName("계획이 있었지만 하나도 못 끝낸 날은 계획이 없던 날과 막대 높이로 구분된다")
    void barHeightDistinguishesEmptyDay() {
        StudyStatistics stats = StudyStatistics.of(List.of(
                plan(PlanCategory.MAJOR, MONDAY, null, null, false)
        ), MONDAY, SUNDAY);

        StudyStatistics.DailyStat plannedButUnfinished = stats.dailyTrend().get(0);
        StudyStatistics.DailyStat noPlanDay = stats.dailyTrend().get(1);

        assertThat(plannedButUnfinished.completionRate()).isZero();
        assertThat(plannedButUnfinished.barHeight()).isPositive();
        assertThat(noPlanDay.barHeight()).isZero();
    }
}
