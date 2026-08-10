package com.example.board.home.dto;

import com.example.board.dday.domain.Dday;
import com.example.board.home.domain.GoalProgress;
import com.example.board.plan.domain.Plan;
import com.example.board.stats.domain.StudyStatistics;

import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 화면이 필요한 값만 모아 둔 불변 객체.
 *
 * <p>여러 모듈에서 온 값을 화면 관점으로 다시 정리하는 자리다.
 * "남은 일정을 몇 개까지 보여 줄지", "다음에 할 일이 무엇인지" 같은 판단이
 * 템플릿이 아니라 여기 있어야 테스트할 수 있다.</p>
 */
public record DashboardView(String nickname,
                            LocalDate today,
                            List<Dday> upcomingDdays,
                            int streak,
                            GoalProgress goal,
                            int planTotal,
                            int planCompleted,
                            List<Plan> openPlans,
                            int hiddenOpenPlanCount,
                            StudyStatistics week) {

    /** 대시보드에 한 번에 보여 줄 남은 일정 개수 - 더 있으면 "+N개" 로 접는다 */
    private static final int OPEN_PLAN_LIMIT = 4;

    public static DashboardView of(String nickname,
                                   LocalDate today,
                                   List<Plan> todayPlans,
                                   StudyStatistics todayStatistics,
                                   StudyStatistics weekStatistics,
                                   List<Dday> upcomingDdays,
                                   int streak,
                                   int dailyGoalMinutes) {
        List<Plan> open = todayPlans.stream().filter(plan -> !plan.isCompleted()).toList();

        return new DashboardView(nickname, today, upcomingDdays, streak,
                GoalProgress.of(todayStatistics.actualMinutes(), dailyGoalMinutes),
                todayStatistics.totalCount(), todayStatistics.completedCount(),
                open.stream().limit(OPEN_PLAN_LIMIT).toList(),
                Math.max(open.size() - OPEN_PLAN_LIMIT, 0),
                weekStatistics);
    }

    /** 지금 바로 시작할 일 - 남은 일정 중 첫 번째 (없으면 null) */
    public Plan nextPlan() {
        return openPlans.isEmpty() ? null : openPlans.get(0);
    }

    public boolean hasNextPlan() {
        return !openPlans.isEmpty();
    }

    /** 오늘 계획을 하나도 세우지 않은 상태 */
    public boolean hasNoPlan() {
        return planTotal == 0;
    }

    /** 계획을 세웠고 남김없이 끝낸 상태 */
    public boolean allPlansDone() {
        return planTotal > 0 && planCompleted == planTotal;
    }

    public int planCompletionRate() {
        return planTotal == 0 ? 0 : (int) Math.round(planCompleted * 100.0 / planTotal);
    }
}
