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

    private StudySession session(PlanCategory category, LocalDate date, int minutes) {
        LocalDateTime start = date.atTime(LocalTime.of(9, 0));
        StudySession session = StudySession.start(author, null, category, start);
        session.stop(start.plusMinutes(minutes));
        return session;
    }

    @Test
    @DisplayName("기록이 없으면 모든 수치가 0 이고 기간만큼의 일별 항목이 채워진다")
    void empty() {
        StudyStatistics stats = StudyStatistics.of(List.of(), List.of(), MONDAY, SUNDAY);

        assertThat(stats.totalCount()).isZero();
        assertThat(stats.completionRate()).isZero();
        assertThat(stats.plannedMinutes()).isZero();
        assertThat(stats.actualMinutes()).isZero();
        assertThat(stats.executionRate()).isZero();
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

    @Nested
    @DisplayName("계획 시간과 실제 시간")
    class PlannedVsActual {

        @Test
        @DisplayName("계획 시간은 완료 여부와 상관없이 그 기간에 잡아 둔 시간을 모두 더한다")
        void plannedMinutesCoversEveryPlan() {
            StudyStatistics stats = StudyStatistics.of(List.of(
                    plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true),  // 180분
                    plan(PlanCategory.MAJOR, MONDAY, LocalTime.of(13, 0), LocalTime.of(14, 30), false),     // 90분
                    plan(PlanCategory.MAJOR, MONDAY, null, null, true)                                      // 종일 → 0분
            ), MONDAY, SUNDAY);

            assertThat(stats.plannedMinutes()).isEqualTo(270);
            assertThat(stats.plannedHours()).isEqualTo(4);
            assertThat(stats.plannedRemainderMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("실제 시간은 학습 세션에서 온다 - 계획을 완료로 눌러도 늘어나지 않는다")
        void actualMinutesComesFromSessions() {
            StudyStatistics stats = StudyStatistics.of(List.of(
                    plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true)
            ), List.of(), MONDAY, SUNDAY);

            assertThat(stats.plannedMinutes()).isEqualTo(180);
            assertThat(stats.actualMinutes()).isZero();
            assertThat(stats.hasNoStudyRecord()).isTrue();
        }

        @Test
        @DisplayName("3시간 계획하고 2시간 10분 공부했으면 실행률은 72% 다")
        void executionRate() {
            StudyStatistics stats = StudyStatistics.of(
                    List.of(plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true)),
                    List.of(session(PlanCategory.CODING_TEST, MONDAY, 130)),
                    MONDAY, SUNDAY);

            assertThat(stats.actualHours()).isEqualTo(2);
            assertThat(stats.actualRemainderMinutes()).isEqualTo(10);
            assertThat(stats.executionRate()).isEqualTo(72);
        }

        @Test
        @DisplayName("계획에 없던 공부도 실제 시간에 잡힌다")
        void unplannedStudyCounts() {
            StudyStatistics stats = StudyStatistics.of(List.of(),
                    List.of(session(PlanCategory.INTERVIEW, MONDAY, 50)), MONDAY, SUNDAY);

            assertThat(stats.totalCount()).isZero();
            assertThat(stats.actualMinutes()).isEqualTo(50);
            assertThat(stats.executionRate()).isZero(); // 계획이 없으면 실행률은 의미가 없다
        }

        @Test
        @DisplayName("진행 중인 세션은 아직 시간이 확정되지 않아 합산되지 않는다")
        void runningSessionIsNotCounted() {
            StudySession running = StudySession.start(author, null, PlanCategory.MAJOR,
                    MONDAY.atTime(LocalTime.of(9, 0)));

            StudyStatistics stats = StudyStatistics.of(List.of(), List.of(running), MONDAY, SUNDAY);

            assertThat(stats.actualMinutes()).isZero();
        }
    }

    @Nested
    @DisplayName("분류별 집계")
    class Categories {

        @Test
        @DisplayName("실제 공부 시간이 많은 순으로 정렬하고 그 비중으로 막대를 그린다")
        void sortedByActualMinutes() {
            StudyStatistics stats = StudyStatistics.of(
                    List.of(plan(PlanCategory.RESUME, MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), true)),
                    List.of(session(PlanCategory.CODING_TEST, MONDAY, 180),
                            session(PlanCategory.RESUME, MONDAY, 60)),
                    MONDAY, SUNDAY);

            assertThat(stats.categories()).extracting(StudyStatistics.CategoryStat::category)
                    .containsExactly(PlanCategory.CODING_TEST, PlanCategory.RESUME);
            assertThat(stats.categories().get(0).share()).isEqualTo(75); // 180 / 240
            assertThat(stats.categories().get(1).share()).isEqualTo(25);
        }

        @Test
        @DisplayName("계획에만 있는 분류와 실제 공부에만 있는 분류가 모두 나온다")
        void mergesBothSources() {
            StudyStatistics stats = StudyStatistics.of(
                    List.of(plan(PlanCategory.RESUME, MONDAY, null, null, false)),
                    List.of(session(PlanCategory.INTERVIEW, MONDAY, 30)),
                    MONDAY, SUNDAY);

            assertThat(stats.categories()).extracting(StudyStatistics.CategoryStat::category)
                    .containsExactlyInAnyOrder(PlanCategory.RESUME, PlanCategory.INTERVIEW);
        }

        @Test
        @DisplayName("실제 기록이 없으면 계획 시간 기준으로 비중을 계산한다")
        void fallsBackToPlannedMinutes() {
            StudyStatistics stats = StudyStatistics.of(List.of(
                    plan(PlanCategory.RESUME, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), true),      // 60분
                    plan(PlanCategory.CODING_TEST, MONDAY, LocalTime.of(10, 0), LocalTime.of(13, 0), true) // 180분
            ), MONDAY, SUNDAY);

            assertThat(stats.categories().get(0).category()).isEqualTo(PlanCategory.CODING_TEST);
            assertThat(stats.categories().get(0).share()).isEqualTo(75);
            assertThat(stats.categories().get(1).share()).isEqualTo(25);
        }

        @Test
        @DisplayName("시간 정보가 아예 없으면 계획 개수 기준으로 비중을 계산한다")
        void fallsBackToCount() {
            StudyStatistics stats = StudyStatistics.of(List.of(
                    plan(PlanCategory.CODING_TEST, MONDAY, null, null, false),
                    plan(PlanCategory.CODING_TEST, MONDAY, null, null, false),
                    plan(PlanCategory.INTERVIEW, MONDAY, null, null, false)
            ), MONDAY, SUNDAY);

            assertThat(stats.categories().get(0).share()).isEqualTo(67); // 2/3
        }
    }

    @Nested
    @DisplayName("일별 추이")
    class DailyTrend {

        @Test
        @DisplayName("기록이 없는 날도 빈 항목으로 채운다")
        void fillsGaps() {
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
        @DisplayName("계획이 있었지만 하나도 못 끝낸 날은 아무 기록도 없던 날과 막대 높이로 구분된다")
        void barHeightDistinguishesEmptyDay() {
            StudyStatistics stats = StudyStatistics.of(List.of(
                    plan(PlanCategory.MAJOR, MONDAY, null, null, false)
            ), MONDAY, SUNDAY);

            assertThat(stats.dailyTrend().get(0).barHeight()).isPositive();
            assertThat(stats.dailyTrend().get(1).barHeight()).isZero();
        }

        @Test
        @DisplayName("계획 없이 공부만 한 날도 빈 날로 취급하지 않는다")
        void studyOnlyDayIsNotEmpty() {
            StudyStatistics stats = StudyStatistics.of(List.of(),
                    List.of(session(PlanCategory.MAJOR, MONDAY, 40)), MONDAY, SUNDAY);

            StudyStatistics.DailyStat monday = stats.dailyTrend().get(0);
            assertThat(monday.isEmpty()).isFalse();
            assertThat(monday.actualMinutes()).isEqualTo(40);
            assertThat(monday.barHeight()).isPositive();
        }
    }
}
