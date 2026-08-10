package com.example.board.stats.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    /** 목표 시간 조건을 끈 상태 - 계획 완료만으로 판정하던 기존 동작 */
    private static final int NO_GOAL = 0;
    private static final int GOAL_30_MIN = 30;

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(LocalDate date, boolean completed) {
        Plan plan = new Plan("공부", null, author, PlanCategory.MAJOR, date, null, null);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    /** 해당 날짜에 minutes 분 만큼 공부한 기록 */
    private StudySession session(LocalDate date, int minutes) {
        LocalDateTime start = date.atTime(LocalTime.of(9, 0));
        StudySession session = StudySession.start(author, null, PlanCategory.MAJOR, start);
        session.stop(start.plusMinutes(minutes));
        return session;
    }

    @Nested
    @DisplayName("계획 완료 기준 (목표 시간을 쓰지 않을 때)")
    class PlanBased {

        @Test
        @DisplayName("오늘 포함 연속으로 모두 완료한 날을 센다")
        void streakIncludingToday() {
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(1), true),
                    plan(TODAY.minusDays(2), true));

            assertThat(StudyStreak.calculate(plans, List.of(), TODAY, NO_GOAL)).isEqualTo(3);
        }

        @Test
        @DisplayName("오늘은 아직 진행 중일 수 있으므로, 오늘이 미완이면 어제부터 센다")
        void todayIncompleteDoesNotBreakStreak() {
            List<Plan> plans = List.of(
                    plan(TODAY, false),
                    plan(TODAY.minusDays(1), true),
                    plan(TODAY.minusDays(2), true));

            assertThat(StudyStreak.calculate(plans, List.of(), TODAY, NO_GOAL)).isEqualTo(2);
        }

        @Test
        @DisplayName("하루라도 미완료 계획이 있으면 그 날에서 연속이 끊긴다")
        void brokenByIncompleteDay() {
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(1), true),
                    plan(TODAY.minusDays(1), false), // 어제는 일부만 완료
                    plan(TODAY.minusDays(2), true));

            assertThat(StudyStreak.calculate(plans, List.of(), TODAY, NO_GOAL)).isEqualTo(1);
        }

        @Test
        @DisplayName("아무 활동도 없는 날에서 연속이 끊긴다")
        void brokenByEmptyDay() {
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(2), true)); // 어제는 계획도 공부도 없음

            assertThat(StudyStreak.calculate(plans, List.of(), TODAY, NO_GOAL)).isEqualTo(1);
        }

        @Test
        @DisplayName("아무 기록도 없으면 0 이다")
        void nothingRecorded() {
            assertThat(StudyStreak.calculate(List.of(), List.of(), TODAY, NO_GOAL)).isZero();
        }
    }

    @Nested
    @DisplayName("목표 학습시간 기준")
    class GoalBased {

        @Test
        @DisplayName("계획만 체크하고 실제로 공부하지 않았다면 달성으로 치지 않는다")
        void checkingPlansWithoutStudyingDoesNotCount() {
            // 이전 규칙의 허점: 계획 1개를 만들고 완료만 눌러도 연속이 이어졌다
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(1), true));

            assertThat(StudyStreak.calculate(plans, List.of(), TODAY, GOAL_30_MIN)).isZero();
        }

        @Test
        @DisplayName("목표 시간을 채우고 계획도 모두 끝낸 날만 이어진다")
        void countsDaysMeetingGoal() {
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(1), true));
            List<StudySession> sessions = List.of(
                    session(TODAY, 45),
                    session(TODAY.minusDays(1), 60));

            assertThat(StudyStreak.calculate(plans, sessions, TODAY, GOAL_30_MIN)).isEqualTo(2);
        }

        @Test
        @DisplayName("목표에 미치지 못한 날에서 연속이 끊긴다")
        void brokenByShortDay() {
            List<Plan> plans = List.of(
                    plan(TODAY, true),
                    plan(TODAY.minusDays(1), true),
                    plan(TODAY.minusDays(2), true));
            List<StudySession> sessions = List.of(
                    session(TODAY, 40),
                    session(TODAY.minusDays(1), 10),  // 목표 미달
                    session(TODAY.minusDays(2), 90));

            assertThat(StudyStreak.calculate(plans, sessions, TODAY, GOAL_30_MIN)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날 여러 번 나눠 공부했으면 합산해서 판정한다")
        void sumsSessionsOfSameDay() {
            List<StudySession> sessions = List.of(
                    session(TODAY, 20),
                    session(TODAY, 15));

            assertThat(StudyStreak.calculate(List.of(), sessions, TODAY, GOAL_30_MIN)).isEqualTo(1);
        }

        @Test
        @DisplayName("계획이 없던 날도 목표 시간을 채웠으면 달성으로 친다")
        void unplannedStudyCounts() {
            List<StudySession> sessions = List.of(session(TODAY, 90));

            assertThat(StudyStreak.calculate(List.of(), sessions, TODAY, GOAL_30_MIN)).isEqualTo(1);
        }

        @Test
        @DisplayName("목표 시간을 채웠어도 남긴 계획이 있으면 달성이 아니다")
        void unfinishedPlanBlocksAchievement() {
            List<Plan> plans = List.of(plan(TODAY.minusDays(1), false));
            List<StudySession> sessions = List.of(
                    session(TODAY, 60),
                    session(TODAY.minusDays(1), 120));

            // 오늘(계획 없음 + 60분)은 달성, 어제는 계획을 남겨 끊긴다
            assertThat(StudyStreak.calculate(plans, sessions, TODAY, GOAL_30_MIN)).isEqualTo(1);
        }

        @Test
        @DisplayName("오늘 목표를 아직 못 채웠어도 어제까지의 연속은 유지된다")
        void todayInProgressKeepsYesterdayStreak() {
            List<StudySession> sessions = List.of(
                    session(TODAY, 5),                  // 아직 진행 중
                    session(TODAY.minusDays(1), 60),
                    session(TODAY.minusDays(2), 60));

            assertThat(StudyStreak.calculate(List.of(), sessions, TODAY, GOAL_30_MIN)).isEqualTo(2);
        }
    }
}
