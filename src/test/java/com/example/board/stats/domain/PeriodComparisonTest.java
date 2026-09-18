package com.example.board.stats.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodComparisonTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private final Member owner = new Member("tester1", "encoded-password", "테스터");

    private StudySession session(int minutes) {
        LocalDateTime start = MONDAY.atTime(LocalTime.of(9, 0));
        StudySession session = StudySession.start(owner, null, PlanCategory.MAJOR, start);
        session.stop(start.plusMinutes(minutes));
        return session;
    }

    private Plan plan(boolean completed) {
        Plan plan = new Plan("공부", null, owner, PlanCategory.MAJOR, MONDAY, null, null);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    private StudyStatistics stats(List<Plan> plans, int sessionMinutes) {
        List<StudySession> sessions = sessionMinutes > 0 ? List.of(session(sessionMinutes)) : List.of();
        return StudyStatistics.of(plans, sessions, MONDAY, SUNDAY);
    }

    private PeriodComparison comparison(StudyStatistics current, StudyStatistics previous) {
        return new PeriodComparison(current, previous, "지난주");
    }

    @Test
    @DisplayName("늘어난 공부 시간을 '지난주 대비 +2시간' 으로 읽어 준다 - 12시간이 잘한 것인지는 지난주를 알아야 안다")
    void increased() {
        PeriodComparison comparison = comparison(stats(List.of(), 300), stats(List.of(), 180));

        assertThat(comparison.actualMinutesDelta()).isEqualTo(120);
        assertThat(comparison.actualIncreased()).isTrue();
        assertThat(comparison.readableActualDelta()).isEqualTo("지난주 대비 +2시간");
    }

    @Test
    @DisplayName("줄었으면 부호도 함께 뒤집는다 - '-30분' 이지 '+-30분' 이 아니다")
    void decreased() {
        PeriodComparison comparison = comparison(stats(List.of(), 60), stats(List.of(), 90));

        assertThat(comparison.actualDecreased()).isTrue();
        assertThat(comparison.readableActualDelta()).isEqualTo("지난주 대비 -30분");
    }

    @Test
    @DisplayName("차이가 없으면 '지난주와 같음' 이다 - '+0분' 은 읽는 사람을 멈칫하게 한다")
    void same() {
        PeriodComparison comparison = comparison(stats(List.of(), 120), stats(List.of(), 120));

        assertThat(comparison.readableActualDelta()).isEqualTo("지난주와 같음");
        assertThat(comparison.actualIncreased()).isFalse();
        assertThat(comparison.actualDecreased()).isFalse();
    }

    @Test
    @DisplayName("완료율의 차이는 %p 로 적는다 - 비율의 차이는 비율이 아니다")
    void completionRate() {
        PeriodComparison comparison = comparison(
                stats(List.of(plan(true), plan(true), plan(false), plan(false)), 0), // 50%
                stats(List.of(plan(true), plan(false), plan(false), plan(false)), 0)); // 25%

        assertThat(comparison.completionRateDelta()).isEqualTo(25);
        assertThat(comparison.readableCompletionRateDelta()).isEqualTo("지난주 대비 +25%p");
    }

    @Test
    @DisplayName("지난 기간에 아무 기록이 없으면 비교하지 않는다 - 없던 지난주를 있었던 것처럼 말하지 않는다")
    void noPrevious() {
        PeriodComparison comparison = comparison(stats(List.of(plan(true)), 180), stats(List.of(), 0));

        assertThat(comparison.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("지난 기간에 계획만 있고 공부 기록이 없어도 비교 대상이다 - 계획을 세웠다는 것도 기록이다")
    void previousWithPlansOnly() {
        PeriodComparison comparison = comparison(stats(List.of(plan(true)), 180), stats(List.of(plan(false)), 0));

        assertThat(comparison.hasPrevious()).isTrue();
        assertThat(comparison.readableActualDelta()).isEqualTo("지난주 대비 +3시간");
    }

    @Test
    @DisplayName("달 단위 비교는 이름만 바뀐다 - 문구를 따로 적으면 한쪽만 고쳐진다")
    void monthlyLabel() {
        PeriodComparison comparison =
                new PeriodComparison(stats(List.of(), 300), stats(List.of(), 180), "지난달");

        assertThat(comparison.readableActualDelta()).isEqualTo("지난달 대비 +2시간");
    }
}
