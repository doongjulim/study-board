package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * 기간별 학습 통계. 플랜·세션 목록만 있으면 계산되는 순수 값 객체라 DB 없이 검증할 수 있다.
 *
 * <p><b>계획 시간과 실제 시간을 나란히 둔다.</b>
 * {@code plannedMinutes} 는 "하기로 한 시간"({@link Plan}) 이고,
 * {@code actualMinutes} 는 "실제로 앉아 있던 시간"({@link StudySession}) 이다.
 * 둘을 함께 보여 줘야 "내가 계획을 과하게 잡는구나" 같은 판단이 가능해진다.</p>
 *
 * <p>계획에 없던 공부도 세션으로 기록되므로, 분류·일별 집계는 두 출처를 합쳐 만든다.</p>
 */
public record StudyStatistics(LocalDate from, LocalDate to,
                              int totalCount, int completedCount,
                              long plannedMinutes, long actualMinutes,
                              List<CategoryStat> categories, List<DailyStat> dailyTrend) {

    /** 타이머를 쓰지 않아 학습 기록이 없는 경우 (실제 시간은 0 으로 집계된다) */
    public static StudyStatistics of(List<Plan> plans, LocalDate from, LocalDate to) {
        return of(plans, List.of(), from, to);
    }

    public static StudyStatistics of(List<Plan> plans, List<StudySession> sessions,
                                     LocalDate from, LocalDate to) {
        int totalCount = plans.size();
        int completedCount = (int) plans.stream().filter(Plan::isCompleted).count();
        long plannedMinutes = plans.stream().mapToLong(Plan::getStudyMinutes).sum();
        long actualMinutes = sessions.stream().mapToLong(StudySession::minutes).sum();

        return new StudyStatistics(from, to, totalCount, completedCount, plannedMinutes, actualMinutes,
                categoryStats(plans, sessions), dailyTrend(plans, sessions, from, to));
    }

    /** 완료율(%) - 계획 개수 기준 */
    public int completionRate() {
        return percent(completedCount, totalCount);
    }

    /** 실행률(%) - 계획한 시간 대비 실제로 공부한 시간 */
    public int executionRate() {
        return percent(actualMinutes, plannedMinutes);
    }

    public long actualHours() {
        return actualMinutes / 60;
    }

    public long actualRemainderMinutes() {
        return actualMinutes % 60;
    }

    public long plannedHours() {
        return plannedMinutes / 60;
    }

    public long plannedRemainderMinutes() {
        return plannedMinutes % 60;
    }

    /** 계획을 세우기만 하고 타이머를 쓰지 않은 상태 - 화면에서 안내를 띄우는 데 쓴다 */
    public boolean hasNoStudyRecord() {
        return actualMinutes == 0;
    }

    private static List<CategoryStat> categoryStats(List<Plan> plans, List<StudySession> sessions) {
        Map<PlanCategory, List<Plan>> plansByCategory = plans.stream()
                .collect(Collectors.groupingBy(Plan::getCategory));
        Map<PlanCategory, Long> actualByCategory = sessions.stream()
                .collect(Collectors.groupingBy(StudySession::getCategory,
                        Collectors.summingLong(StudySession::minutes)));

        // 계획에만 있는 분류와 실제 공부에만 있는 분류를 모두 포함한다
        Set<PlanCategory> categories = new LinkedHashSet<>(plansByCategory.keySet());
        categories.addAll(actualByCategory.keySet());

        long totalActual = actualByCategory.values().stream().mapToLong(Long::longValue).sum();
        long totalPlanned = plans.stream().mapToLong(Plan::getStudyMinutes).sum();

        return categories.stream()
                .map(category -> {
                    List<Plan> categoryPlans = plansByCategory.getOrDefault(category, List.of());
                    long planned = categoryPlans.stream().mapToLong(Plan::getStudyMinutes).sum();
                    long actual = actualByCategory.getOrDefault(category, 0L);
                    int completed = (int) categoryPlans.stream().filter(Plan::isCompleted).count();
                    return new CategoryStat(category, categoryPlans.size(), completed,
                            planned, actual,
                            share(actual, totalActual, planned, totalPlanned,
                                    categoryPlans.size(), plans.size()));
                })
                .sorted(Comparator.comparingLong(CategoryStat::actualMinutes).reversed()
                        .thenComparing(Comparator.comparingLong(CategoryStat::plannedMinutes).reversed())
                        .thenComparing(Comparator.comparingInt(CategoryStat::totalCount).reversed()))
                .toList();
    }

    /**
     * 막대 길이에 쓸 비중(%).
     * 실제 공부 시간이 있으면 그 기준으로, 없으면 계획 시간으로, 그마저 없으면 개수로 그린다.
     */
    private static int share(long actual, long totalActual,
                             long planned, long totalPlanned,
                             int count, int totalCount) {
        if (totalActual > 0) {
            return percent(actual, totalActual);
        }
        if (totalPlanned > 0) {
            return percent(planned, totalPlanned);
        }
        return percent(count, totalCount);
    }

    private static List<DailyStat> dailyTrend(List<Plan> plans, List<StudySession> sessions,
                                              LocalDate from, LocalDate to) {
        Map<LocalDate, List<Plan>> plansByDate = plans.stream()
                .collect(Collectors.groupingBy(Plan::getPlanDate));
        Map<LocalDate, Long> minutesByDate = sessions.stream()
                .collect(Collectors.groupingBy(StudySession::getStudyDate,
                        Collectors.summingLong(StudySession::minutes)));

        List<DailyStat> trend = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<Plan> dayPlans = plansByDate.getOrDefault(date, List.of());
            int completed = (int) dayPlans.stream().filter(Plan::isCompleted).count();
            trend.add(new DailyStat(date, dayPlans.size(), completed,
                    minutesByDate.getOrDefault(date, 0L)));
        }
        return trend;
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    /** 분류별 집계 - share 는 막대 길이에 쓰는 비중(%) */
    public record CategoryStat(PlanCategory category, int totalCount, int completedCount,
                               long plannedMinutes, long actualMinutes, int share) {

        public int completionRate() {
            return percent(completedCount, totalCount);
        }

        public long actualHours() {
            return actualMinutes / 60;
        }

        public long actualRemainderMinutes() {
            return actualMinutes % 60;
        }

        public long plannedHours() {
            return plannedMinutes / 60;
        }

        public long plannedRemainderMinutes() {
            return plannedMinutes % 60;
        }
    }

    /** 일별 집계 - 추이 막대에 사용 */
    public record DailyStat(LocalDate date, int totalCount, int completedCount, long actualMinutes) {

        /** 계획이 있었지만 하나도 못 끝낸 날에도 최소 높이를 줘, 아무 기록도 없던 날과 구분되게 한다 */
        private static final int MIN_BAR_HEIGHT = 6;

        public int completionRate() {
            return percent(completedCount, totalCount);
        }

        /** 계획도 학습 기록도 없는 날 */
        public boolean isEmpty() {
            return totalCount == 0 && actualMinutes == 0;
        }

        public int barHeight() {
            return isEmpty() ? 0 : Math.max(completionRate(), MIN_BAR_HEIGHT);
        }

        public long actualHours() {
            return actualMinutes / 60;
        }

        public long actualRemainderMinutes() {
            return actualMinutes % 60;
        }
    }
}
