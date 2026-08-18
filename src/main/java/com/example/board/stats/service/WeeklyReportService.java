package com.example.board.stats.service;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.repository.PlanRepository;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.service.RetrospectiveService;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import com.example.board.stats.domain.WeeklyReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** 한 주의 학습 기록으로 게시글 초안을 만들어 준다 (게시판 '인증글' 연동용) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyReportService {

    private final PlanRepository planRepository;
    private final StudySessionRepository sessionRepository;
    /** 이미 적어 둔 회고를 초안에 옮기기 위한 읽기 전용 의존 */
    private final RetrospectiveService retrospectiveService;

    public WeeklyReport draft(Long memberId, LocalDate anyDayOfWeek) {
        LocalDate weekStart = anyDayOfWeek.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<Plan> plans = planRepository
                .findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                        memberId, weekStart, weekEnd);
        List<StudySession> sessions = sessionRepository
                .findByOwner_IdAndStudyDateBetweenOrderByStartedAtAsc(memberId, weekStart, weekEnd);

        return WeeklyReport.of(plans, sessions,
                retrospectiveService.findDailies(memberId, weekStart, weekEnd),
                retrospectiveService.find(memberId, RetroType.WEEKLY, weekStart).orElse(null),
                weekStart, weekEnd);
    }
}
