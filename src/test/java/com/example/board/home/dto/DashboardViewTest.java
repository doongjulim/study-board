package com.example.board.home.dto;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import com.example.board.stats.domain.StudyStatistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DashboardView")
class DashboardViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);
    private static final int GOAL = 60;

    private final Member member = new Member("tester1", "encoded-password", "동주");

    private Plan plan(String title, LocalTime start, boolean completed) {
        Plan plan = new Plan(title, null, member, PlanCategory.CODING_TEST, TODAY, start,
                start == null ? null : start.plusHours(1));
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    private StudySession session(int minutes) {
        LocalDateTime start = TODAY.atTime(LocalTime.of(9, 0));
        StudySession session = StudySession.start(member, null, PlanCategory.MAJOR, start);
        session.stop(start.plusMinutes(minutes));
        return session;
    }

    private DashboardView view(List<Plan> plans, List<StudySession> sessions) {
        return DashboardView.of("동주", TODAY, plans,
                StudyStatistics.of(plans, sessions, TODAY, TODAY),
                StudyStatistics.of(plans, sessions, TODAY.minusDays(6), TODAY),
                List.of(), 3, GOAL);
    }

    @Nested
    @DisplayName("남은 일정")
    class OpenPlans {

        @Test
        @DisplayName("완료한 일정은 빼고 보여 준다")
        void excludesCompleted() {
            DashboardView view = view(List.of(
                    plan("끝난 것", LocalTime.of(9, 0), true),
                    plan("남은 것", LocalTime.of(11, 0), false)), List.of());

            assertThat(view.openPlans()).extracting(Plan::getTitle).containsExactly("남은 것");
            assertThat(view.planTotal()).isEqualTo(2);
            assertThat(view.planCompleted()).isEqualTo(1);
            assertThat(view.planCompletionRate()).isEqualTo(50);
        }

        @Test
        @DisplayName("네 개까지만 보여 주고 나머지는 개수로 접는다")
        void limitsToFour() {
            DashboardView view = view(List.of(
                    plan("1", LocalTime.of(9, 0), false),
                    plan("2", LocalTime.of(10, 0), false),
                    plan("3", LocalTime.of(11, 0), false),
                    plan("4", LocalTime.of(12, 0), false),
                    plan("5", LocalTime.of(13, 0), false),
                    plan("6", LocalTime.of(14, 0), false)), List.of());

            assertThat(view.openPlans()).hasSize(4);
            assertThat(view.hiddenOpenPlanCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("접을 것이 없으면 숨긴 개수는 0 이다")
        void nothingHidden() {
            DashboardView view = view(List.of(plan("하나", LocalTime.of(9, 0), false)), List.of());

            assertThat(view.hiddenOpenPlanCount()).isZero();
        }
    }

    @Nested
    @DisplayName("다음에 할 일")
    class NextPlan {

        @Test
        @DisplayName("남은 일정 중 첫 번째를 다음 할 일로 제시한다")
        void picksFirstOpenPlan() {
            DashboardView view = view(List.of(
                    plan("이미 함", LocalTime.of(9, 0), true),
                    plan("다음 차례", LocalTime.of(11, 0), false),
                    plan("그 다음", LocalTime.of(13, 0), false)), List.of());

            assertThat(view.hasNextPlan()).isTrue();
            assertThat(view.nextPlan().getTitle()).isEqualTo("다음 차례");
        }

        @Test
        @DisplayName("남은 일정이 없으면 다음 할 일도 없다")
        void noneLeft() {
            DashboardView view = view(List.of(plan("끝", LocalTime.of(9, 0), true)), List.of());

            assertThat(view.hasNextPlan()).isFalse();
            assertThat(view.nextPlan()).isNull();
            assertThat(view.allPlansDone()).isTrue();
        }
    }

    @Nested
    @DisplayName("오늘 상태")
    class TodayState {

        @Test
        @DisplayName("계획을 하나도 세우지 않은 날을 구분한다")
        void noPlanAtAll() {
            DashboardView view = view(List.of(), List.of());

            assertThat(view.hasNoPlan()).isTrue();
            assertThat(view.allPlansDone()).isFalse();
        }

        @Test
        @DisplayName("진행률 링은 실제 공부한 시간을 목표와 견준다")
        void goalProgressUsesActualMinutes() {
            DashboardView view = view(
                    List.of(plan("계획", LocalTime.of(9, 0), true)),
                    List.of(session(30)));

            assertThat(view.goal().actualMinutes()).isEqualTo(30);
            assertThat(view.goal().percent()).isEqualTo(50);
            assertThat(view.goal().remainingMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("계획을 완료로 눌러도 진행률 링은 움직이지 않는다")
        void checkingPlansDoesNotFillTheRing() {
            DashboardView view = view(
                    List.of(plan("계획만 체크", LocalTime.of(9, 0), true)), List.of());

            assertThat(view.allPlansDone()).isTrue();
            assertThat(view.goal().percent()).isZero();
        }
    }
}
