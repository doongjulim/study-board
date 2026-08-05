package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연속 달성일 계산.
 * "그날 계획이 1개 이상 있고 전부 완료" 한 날이 며칠째 이어지는지를 센다.
 * 오늘은 아직 진행 중일 수 있으므로, 오늘이 미완이면 어제부터 거슬러 센다.
 */
public final class StudyStreak {

    private StudyStreak() {
    }

    public static int calculate(List<Plan> plans, LocalDate today) {
        Map<LocalDate, List<Plan>> byDate = plans.stream()
                .collect(Collectors.groupingBy(Plan::getPlanDate));

        LocalDate cursor = isAchieved(byDate, today) ? today : today.minusDays(1);
        int streak = 0;
        while (isAchieved(byDate, cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static boolean isAchieved(Map<LocalDate, List<Plan>> byDate, LocalDate date) {
        List<Plan> dayPlans = byDate.get(date);
        return dayPlans != null && !dayPlans.isEmpty() && dayPlans.stream().allMatch(Plan::isCompleted);
    }
}
