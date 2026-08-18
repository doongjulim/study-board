package com.example.board.stats.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.domain.Retrospective;
import com.example.board.session.domain.StudySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(String title, PlanCategory category, LocalDate date,
                      LocalTime start, LocalTime end, boolean completed) {
        Plan plan = new Plan(title, null, author, category, date, start, end);
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

    private Retrospective retro(RetroType type, LocalDate date, String content) {
        return Retrospective.write(author, type, date, content);
    }

    @Test
    @DisplayName("주간 회고는 숫자 바로 아래에 실린다 - 남이 읽을 이유가 되는 유일한 부분이다")
    void weeklyRetroGoesRightAfterNumbers() {
        WeeklyReport report = WeeklyReport.of(List.of(), List.of(), List.of(),
                retro(RetroType.WEEKLY, MONDAY, "계획을 과하게 잡았다"), MONDAY, SUNDAY);

        assertThat(report.content()).contains("📝 이번 주 회고", "계획을 과하게 잡았다");
        assertThat(report.content().indexOf("📝 이번 주 회고"))
                .isGreaterThan(report.content().indexOf("총 공부 시간"));
    }

    @Test
    @DisplayName("하루 회고는 날짜와 함께 한 줄씩 실린다")
    void dailyRetrosBecomeLines() {
        WeeklyReport report = WeeklyReport.of(List.of(), List.of(),
                List.of(retro(RetroType.DAILY, MONDAY, "오전에 집중 잘 됨"),
                        retro(RetroType.DAILY, MONDAY.plusDays(1), "저녁에 늘어짐")),
                null, MONDAY, SUNDAY);

        assertThat(report.content()).contains("🗒 하루 회고", "8/3(월) 오전에 집중 잘 됨", "8/4(화) 저녁에 늘어짐");
    }

    @Test
    @DisplayName("회고를 쓰지 않은 주에는 회고 칸이 아예 나오지 않는다")
    void noRetroMeansNoSection() {
        WeeklyReport report = WeeklyReport.of(List.of(), MONDAY, SUNDAY);

        assertThat(report.content()).doesNotContain("📝 이번 주 회고", "🗒 하루 회고");
    }

    @Test
    @DisplayName("제목에 주간 범위가 들어간다")
    void title() {
        WeeklyReport report = WeeklyReport.of(List.of(), MONDAY, SUNDAY);

        assertThat(report.title()).isEqualTo("[8/3~8/9] 이번 주 학습 인증");
    }

    @Test
    @DisplayName("완료율과 실제 공부 시간, 계획 대비 실행률을 요약한다")
    void summary() {
        WeeklyReport report = WeeklyReport.of(List.of(
                plan("DP 복습", PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true),
                plan("자소서", PlanCategory.RESUME, MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 30), false)
        ), List.of(session(PlanCategory.CODING_TEST, MONDAY, 180)), MONDAY, SUNDAY);

        assertThat(report.content()).contains("이번 주 완료율 50% (1/2)");
        assertThat(report.content()).contains("총 공부 시간 3시간 0분");
        assertThat(report.content()).contains("계획 4시간 30분 대비 67%"); // 180 / 270
    }

    @Test
    @DisplayName("계획을 세우지 않은 주에는 실행률을 적지 않는다")
    void noPlannedTimeSkipsExecutionRate() {
        WeeklyReport report = WeeklyReport.of(List.of(),
                List.of(session(PlanCategory.MAJOR, MONDAY, 45)), MONDAY, SUNDAY);

        assertThat(report.content()).contains("총 공부 시간 0시간 45분");
        assertThat(report.content()).doesNotContain("대비");
    }

    @Test
    @DisplayName("완료한 계획과 남은 계획을 나눠 적는다")
    void separatesCompletedAndRemaining() {
        WeeklyReport report = WeeklyReport.of(List.of(
                plan("DP 복습", PlanCategory.CODING_TEST, MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), true),
                plan("자소서 1번", PlanCategory.RESUME, MONDAY.plusDays(2), null, null, false)
        ), MONDAY, SUNDAY);

        assertThat(report.content()).contains("✅ 해낸 계획");
        assertThat(report.content()).contains("- 8/3(월) 코딩테스트 · DP 복습 (3시간)");
        assertThat(report.content()).contains("⏳ 남은 계획");
        assertThat(report.content()).contains("- 8/5(수) 자소서 · 자소서 1번");
    }

    @Test
    @DisplayName("계획 시간이 30분 단위로 걸쳐 있으면 시간과 분을 함께 적는다")
    void durationWithMinutes() {
        WeeklyReport report = WeeklyReport.of(List.of(
                plan("모의면접", PlanCategory.INTERVIEW, MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 30), true)
        ), MONDAY, SUNDAY);

        assertThat(report.content()).contains("(1시간 30분)");
    }

    @Test
    @DisplayName("분류별로 실제 공부한 시간을 덧붙인다")
    void categoryBreakdown() {
        WeeklyReport report = WeeklyReport.of(
                List.of(plan("DP 복습", PlanCategory.CODING_TEST, MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(12, 0), true)),
                List.of(session(PlanCategory.CODING_TEST, MONDAY, 150)),
                MONDAY, SUNDAY);

        assertThat(report.content()).contains("📊 분류별 공부 시간");
        assertThat(report.content()).contains("- 코딩테스트 2시간 30분");
    }

    @Test
    @DisplayName("기록이 없으면 목록 없이 요약만 남는다")
    void emptyWeek() {
        WeeklyReport report = WeeklyReport.of(List.of(), MONDAY, SUNDAY);

        assertThat(report.content()).contains("이번 주 완료율 0% (0/0)");
        assertThat(report.content()).doesNotContain("✅ 해낸 계획");
        assertThat(report.content()).doesNotContain("⏳ 남은 계획");
    }
}
