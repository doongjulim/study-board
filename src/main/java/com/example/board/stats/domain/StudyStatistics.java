package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 기간별 학습 통계. 플랜 목록만 있으면 계산되는 순수 값 객체라 DB 없이 검증할 수 있다.
 * 공부 시간은 "실제로 해낸 시간"을 보기 위해 완료한 플랜만 합산한다.
 */
public record StudyStatistics(LocalDate from, LocalDate to,
                              int totalCount, int completedCount, long completedMinutes,
                              List<CategoryStat> categories, List<DailyStat> dailyTrend) {

    public static StudyStatistics of(List<Plan> plans, LocalDate from, LocalDate to) {
        int totalCount = plans.size();
        int completedCount = (int) plans.stream().filter(Plan::isCompleted).count();
        long completedMinutes = plans.stream().filter(Plan::isCompleted)
                .mapToLong(Plan::getStudyMinutes).sum();

        return new StudyStatistics(from, to, totalCount, completedCount, completedMinutes,
                categoryStats(plans, completedMinutes), dailyTrend(plans, from, to));
    }

    /** 완료율(%) - 계획이 없으면 0 */
    public int completionRate() {
        return percent(completedCount, totalCount);
    }

    public long completedHours() {
        return completedMinutes / 60;
    }

    public long remainderMinutes() {
        return completedMinutes % 60;
    }

    private static List<CategoryStat> categoryStats(List<Plan> plans, long totalCompletedMinutes) {
        Map<PlanCategory, List<Plan>> grouped = plans.stream()
                .collect(Collectors.groupingBy(Plan::getCategory));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Plan> categoryPlans = entry.getValue();
                    int completed = (int) categoryPlans.stream().filter(Plan::isCompleted).count();
                    long minutes = categoryPlans.stream().filter(Plan::isCompleted)
                            .mapToLong(Plan::getStudyMinutes).sum();
                    // 시간이 기록된 플랜이 없으면 개수 비중으로 막대를 그린다
                    int share = totalCompletedMinutes > 0
                            ? percent(minutes, totalCompletedMinutes)
                            : percent(categoryPlans.size(), plans.size());
                    return new CategoryStat(entry.getKey(), categoryPlans.size(), completed, minutes, share);
                })
                .sorted(Comparator.comparingLong(CategoryStat::minutes).reversed()
                        .thenComparing(Comparator.comparingInt(CategoryStat::totalCount).reversed()))
                .toList();
    }

    private static List<DailyStat> dailyTrend(List<Plan> plans, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Plan>> byDate = plans.stream()
                .collect(Collectors.groupingBy(Plan::getPlanDate));

        List<DailyStat> trend = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<Plan> dayPlans = byDate.getOrDefault(date, List.of());
            int completed = (int) dayPlans.stream().filter(Plan::isCompleted).count();
            trend.add(new DailyStat(date, dayPlans.size(), completed));
        }
        return trend;
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    /** 분류별 집계 - share 는 막대 길이에 쓰는 비중(%) */
    public record CategoryStat(PlanCategory category, int totalCount, int completedCount,
                               long minutes, int share) {

        public int completionRate() {
            return percent(completedCount, totalCount);
        }

        public long hours() {
            return minutes / 60;
        }

        public long remainderMinutes() {
            return minutes % 60;
        }
    }

    /** 일별 집계 - 추이 막대에 사용 */
    public record DailyStat(LocalDate date, int totalCount, int completedCount) {

        /** 계획이 있었지만 하나도 못 끝낸 날에도 최소 높이를 줘, 계획이 없던 날과 구분되게 한다 */
        private static final int MIN_BAR_HEIGHT = 6;

        public int completionRate() {
            return percent(completedCount, totalCount);
        }

        public boolean isEmpty() {
            return totalCount == 0;
        }

        public int barHeight() {
            return isEmpty() ? 0 : Math.max(completionRate(), MIN_BAR_HEIGHT);
        }
    }
}
