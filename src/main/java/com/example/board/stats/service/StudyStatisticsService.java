package com.example.board.stats.service;

import com.example.board.member.service.MemberService;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.repository.PlanRepository;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import com.example.board.stats.domain.PeriodComparison;
import com.example.board.stats.domain.StatsPeriod;
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
    private final StudySessionRepository sessionRepository;
    /** 하루 목표 시간은 회원 소유 값이므로 member 모듈에 물어본다 (읽기 전용) */
    private final MemberService memberService;

    public StudyStatistics calculate(Long memberId, LocalDate from, LocalDate to) {
        return StudyStatistics.of(findPlans(memberId, from, to), findSessions(memberId, from, to), from, to);
    }

    public StudyStatistics calculate(Long memberId, StatsPeriod period) {
        return calculate(memberId, period.from(), period.to());
    }

    /**
     * 이번 기간과 지난 같은 기간을 함께 계산한다.
     *
     * <p>지난 기간을 한 번 더 조회하는 값이 있다 - "12시간" 만으로는 잘한 것인지 알 수 없고,
     * 판단에 필요한 정보가 화면에 없으면 숫자는 장식이 된다. 두 조회 모두
     * {@code plan (author_id, plan_date)} 인덱스를 탄다.</p>
     */
    public PeriodComparison compare(Long memberId, StatsPeriod period) {
        return new PeriodComparison(calculate(memberId, period),
                calculate(memberId, period.previous()), period.previousLabel());
    }

    public int currentStreak(Long memberId, LocalDate today) {
        LocalDate from = today.minusDays(STREAK_WINDOW_DAYS);
        return StudyStreak.calculate(
                findPlans(memberId, from, today),
                findSessions(memberId, from, today),
                today,
                memberService.findDailyGoalMinutes(memberId));
    }

    private List<Plan> findPlans(Long memberId, LocalDate from, LocalDate to) {
        return planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                memberId, from, to);
    }

    private List<StudySession> findSessions(Long memberId, LocalDate from, LocalDate to) {
        return sessionRepository.findByOwner_IdAndStudyDateBetweenOrderByStartedAtAsc(memberId, from, to);
    }
}
