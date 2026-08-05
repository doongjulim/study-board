package com.example.board.stats.service;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.repository.PlanRepository;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.domain.StudyStreak;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyStatisticsService {

    /** 연속 달성일을 거슬러 셀 최대 범위 - 1년이면 충분하고 조회량도 예측 가능하다 */
    private static final int STREAK_WINDOW_DAYS = 365;

    private final PlanRepository planRepository;

    public StudyStatistics calculate(Long memberId, LocalDate from, LocalDate to) {
        return StudyStatistics.of(findPlans(memberId, from, to), from, to);
    }

    public int currentStreak(Long memberId, LocalDate today) {
        List<Plan> plans = findPlans(memberId, today.minusDays(STREAK_WINDOW_DAYS), today);
        return StudyStreak.calculate(plans, today);
    }

    private List<Plan> findPlans(Long memberId, LocalDate from, LocalDate to) {
        return planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                memberId, from, to);
    }
}
