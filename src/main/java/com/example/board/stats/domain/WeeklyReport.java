package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;
import com.example.board.session.domain.StudySession;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 한 주의 학습 기록을 게시글 초안(제목·본문)으로 옮긴 값 객체.
 * 플랜 목록만으로 만들어지므로 DB 없이 검증할 수 있다.
 */
public record WeeklyReport(String title, String content) {

    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter LINE_DATE =
            DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);

    /** 타이머를 쓰지 않아 학습 기록이 없는 경우 */
    public static WeeklyReport of(List<Plan> plans, LocalDate from, LocalDate to) {
        return of(plans, List.of(), from, to);
    }

    public static WeeklyReport of(List<Plan> plans, List<StudySession> sessions,
                                  LocalDate from, LocalDate to) {
        StudyStatistics statistics = StudyStatistics.of(plans, sessions, from, to);
        return new WeeklyReport(buildTitle(from, to), buildContent(plans, statistics));
    }

    private static String buildTitle(LocalDate from, LocalDate to) {
        return "[%s~%s] 이번 주 학습 인증".formatted(from.format(TITLE_DATE), to.format(TITLE_DATE));
    }

    private static String buildContent(List<Plan> plans, StudyStatistics statistics) {
        StringBuilder content = new StringBuilder();
        content.append("이번 주 완료율 %d%% (%d/%d)%n".formatted(
                statistics.completionRate(), statistics.completedCount(), statistics.totalCount()));
        content.append("총 공부 시간 %d시간 %d분%n".formatted(
                statistics.actualHours(), statistics.actualRemainderMinutes()));
        // 계획을 세워 둔 주에만 대비 실행률을 덧붙인다 (계획이 없으면 비교 대상이 없다)
        if (statistics.plannedMinutes() > 0) {
            content.append("계획 %d시간 %d분 대비 %d%%%n".formatted(
                    statistics.plannedHours(), statistics.plannedRemainderMinutes(),
                    statistics.executionRate()));
        }
        content.append(System.lineSeparator());

        appendPlanLines(content, "✅ 해낸 계획", plans.stream().filter(Plan::isCompleted).toList());
        appendPlanLines(content, "⏳ 남은 계획", plans.stream().filter(plan -> !plan.isCompleted()).toList());

        if (!statistics.categories().isEmpty()) {
            content.append("📊 분류별 공부 시간%n".formatted());
            statistics.categories().forEach(stat -> content.append("- %s %d시간 %d분%n".formatted(
                    stat.category().getLabel(), stat.actualHours(), stat.actualRemainderMinutes())));
        }
        return content.toString().trim();
    }

    private static void appendPlanLines(StringBuilder content, String heading, List<Plan> plans) {
        if (plans.isEmpty()) {
            return;
        }
        content.append(heading).append(System.lineSeparator());
        plans.forEach(plan -> content.append("- %s %s · %s%s%n".formatted(
                plan.getPlanDate().format(LINE_DATE),
                plan.getCategory().getLabel(),
                plan.getTitle(),
                durationSuffix(plan))));
        content.append(System.lineSeparator());
    }

    private static String durationSuffix(Plan plan) {
        long minutes = plan.getStudyMinutes();
        if (minutes <= 0) {
            return "";
        }
        return minutes % 60 == 0
                ? " (%d시간)".formatted(minutes / 60)
                : " (%d시간 %d분)".formatted(minutes / 60, minutes % 60);
    }
}
