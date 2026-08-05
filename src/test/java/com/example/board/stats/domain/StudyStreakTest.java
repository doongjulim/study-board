package com.example.board.stats.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(LocalDate date, boolean completed) {
        Plan plan = new Plan("공부", null, author, PlanCategory.MAJOR, date, null, null);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    @Test
    @DisplayName("오늘 포함 연속으로 모두 완료한 날을 센다")
    void streakIncludingToday() {
        List<Plan> plans = List.of(
                plan(TODAY, true),
                plan(TODAY.minusDays(1), true),
                plan(TODAY.minusDays(2), true));

        assertThat(StudyStreak.calculate(plans, TODAY)).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘은 아직 진행 중일 수 있으므로, 오늘이 미완이면 어제부터 센다")
    void todayIncompleteDoesNotBreakStreak() {
        List<Plan> plans = List.of(
                plan(TODAY, false),
                plan(TODAY.minusDays(1), true),
                plan(TODAY.minusDays(2), true));

        assertThat(StudyStreak.calculate(plans, TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("하루라도 미완료 계획이 있으면 그 날에서 연속이 끊긴다")
    void brokenByIncompleteDay() {
        List<Plan> plans = List.of(
                plan(TODAY, true),
                plan(TODAY.minusDays(1), true),
                plan(TODAY.minusDays(1), false), // 어제는 일부만 완료
                plan(TODAY.minusDays(2), true));

        assertThat(StudyStreak.calculate(plans, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("계획이 아예 없는 날에서도 연속이 끊긴다")
    void brokenByEmptyDay() {
        List<Plan> plans = List.of(
                plan(TODAY, true),
                plan(TODAY.minusDays(2), true)); // 어제는 계획 없음

        assertThat(StudyStreak.calculate(plans, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("계획이 하나도 없으면 0 이다")
    void noPlans() {
        assertThat(StudyStreak.calculate(List.of(), TODAY)).isZero();
    }
}
