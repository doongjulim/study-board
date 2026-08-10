package com.example.board.stats.domain;

import com.example.board.plan.domain.Plan;
import com.example.board.session.domain.StudySession;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연속 달성일 계산.
 *
 * <p>"달성한 날" 은 두 조건을 모두 만족한 날이다.</p>
 * <ol>
 *   <li>실제 학습 시간이 하루 목표 시간 이상 — 계획을 만들고 완료만 눌러서는 이어지지 않는다</li>
 *   <li>그날 세운 계획을 남김없이 완료 — 계획이 없던 날은 이 조건을 자동 통과한다</li>
 * </ol>
 *
 * <p>목표 시간을 0 으로 두면 1번 조건이 꺼져, 계획 완료만으로 판정하던 예전 방식으로 돌아간다.
 * 다만 이 경우에도 아무 기록이 없는 날은 달성으로 치지 않는다.</p>
 *
 * <p>오늘은 아직 진행 중일 수 있으므로, 오늘이 미달이면 어제부터 거슬러 센다.</p>
 */
public final class StudyStreak {

    private StudyStreak() {
    }

    public static int calculate(List<Plan> plans, List<StudySession> sessions,
                                LocalDate today, int dailyGoalMinutes) {
        Map<LocalDate, List<Plan>> plansByDate = plans.stream()
                .collect(Collectors.groupingBy(Plan::getPlanDate));
        Map<LocalDate, Long> minutesByDate = sessions.stream()
                .collect(Collectors.groupingBy(StudySession::getStudyDate,
                        Collectors.summingLong(StudySession::minutes)));

        LocalDate cursor = isAchieved(plansByDate, minutesByDate, today, dailyGoalMinutes)
                ? today
                : today.minusDays(1);

        int streak = 0;
        while (isAchieved(plansByDate, minutesByDate, cursor, dailyGoalMinutes)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static boolean isAchieved(Map<LocalDate, List<Plan>> plansByDate,
                                      Map<LocalDate, Long> minutesByDate,
                                      LocalDate date, int dailyGoalMinutes) {
        List<Plan> dayPlans = plansByDate.getOrDefault(date, List.of());
        long dayMinutes = minutesByDate.getOrDefault(date, 0L);

        boolean hasActivity = !dayPlans.isEmpty() || dayMinutes > 0;
        boolean plansFinished = dayPlans.stream().allMatch(Plan::isCompleted);
        boolean goalMet = dayMinutes >= dailyGoalMinutes;

        return hasActivity && plansFinished && goalMet;
    }
}
