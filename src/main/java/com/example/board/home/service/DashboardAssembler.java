package com.example.board.home.service;

import com.example.board.dday.service.DdayService;
import com.example.board.home.dto.DashboardView;
import com.example.board.member.service.MemberService;
import com.example.board.plan.service.PlanService;
import com.example.board.stats.service.StudyStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 대시보드 한 화면을 위해 여러 모듈의 값을 모으는 조합기.
 *
 * <p>컨트롤러가 네 개의 서비스를 직접 들면 화면 하나 때문에 모듈 간 결합이 퍼진다.
 * 조합 책임을 이 클래스가 혼자 지고, 각 모듈은 서로를 계속 모르는 상태로 둔다.
 * 모든 호출은 <b>읽기 전용</b> 이며 여기서 도메인 상태를 바꾸지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAssembler {

    private final PlanService planService;
    private final DdayService ddayService;
    private final StudyStatisticsService statisticsService;
    private final MemberService memberService;

    public DashboardView assemble(Long memberId, String nickname, LocalDate today) {
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        return DashboardView.of(
                nickname,
                today,
                planService.findDaily(today, memberId),
                statisticsService.calculate(memberId, today, today),
                statisticsService.calculate(memberId, weekStart, weekStart.plusDays(6)),
                ddayService.findUpcoming(memberId, today),
                statisticsService.currentStreak(memberId, today),
                memberService.findDailyGoalMinutes(memberId));
    }
}
