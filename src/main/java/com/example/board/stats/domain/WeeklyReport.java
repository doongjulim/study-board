package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;
import com.example.board.retro.domain.Retrospective;
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

    /** 회고를 쓰지 않은 주 */
    public static WeeklyReport of(List<Plan> plans, List<StudySession> sessions,
                                  LocalDate from, LocalDate to) {
        return of(plans, sessions, List.of(), null, from, to);
    }

    /**
     * 회고까지 실어 초안을 만든다.
     *
     * <p>숫자만 붙은 인증글은 남이 읽을 이유가 없다. 그 주에 무엇을 느꼈는지가 들어가야
     * 글이 되고, 이미 적어 둔 회고를 그대로 옮기면 인증글을 새로 쓰지 않아도 된다.</p>
     */
    public static WeeklyReport of(List<Plan> plans, List<StudySession> sessions,
                                  List<Retrospective> dailyRetros, Retrospective weeklyRetro,
                                  LocalDate from, LocalDate to) {
        StudyStatistics statistics = StudyStatistics.of(plans, sessions, from, to);
        return new WeeklyReport(buildTitle(from, to),
                buildContent(plans, statistics, dailyRetros, weeklyRetro));
    }

    private static String buildTitle(LocalDate from, LocalDate to) {
        return "[%s~%s] 이번 주 학습 인증".formatted(from.format(TITLE_DATE), to.format(TITLE_DATE));
    }

    private static String buildContent(List<Plan> plans, StudyStatistics statistics,
                                       List<Retrospective> dailyRetros, Retrospective weeklyRetro) {
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

        // 주간 회고를 숫자 바로 아래 둔다 - 이 글에서 남이 읽을 이유가 있는 유일한 부분이다
        if (weeklyRetro != null) {
            content.append("📝 이번 주 회고%n".formatted());
            content.append(weeklyRetro.getContent()).append(System.lineSeparator());
            content.append(System.lineSeparator());
        }

        appendPlanLines(content, "✅ 해낸 계획", plans.stream().filter(Plan::isCompleted).toList());
        appendPlanLines(content, "⏳ 남은 계획", plans.stream().filter(plan -> !plan.isCompleted()).toList());

        appendRetroLines(content, dailyRetros);

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

    /** 하루 회고는 날짜와 함께 한 줄씩 - 그 주가 어떻게 흘러갔는지가 드러난다 */
    private static void appendRetroLines(StringBuilder content, List<Retrospective> dailyRetros) {
        if (dailyRetros.isEmpty()) {
            return;
        }
        content.append("🗒 하루 회고%n".formatted());
        dailyRetros.forEach(retro -> content.append("- %s %s%n".formatted(
                retro.getTargetDate().format(LINE_DATE), retro.getContent())));
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
