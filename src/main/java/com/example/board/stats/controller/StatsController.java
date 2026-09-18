package com.example.board.stats.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.stats.domain.PeriodComparison;
import com.example.board.stats.domain.StatsPeriod;
import com.example.board.stats.service.StudyStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StudyStatisticsService statisticsService;
    /** 연속 학습일(streak) 이 "오늘" 기준이라, 시간대가 어긋나면 기록이 하루 밀린다 */
    private final Clock clock;

    /** 학습 통계 대시보드 - 주간/월간 전환 + 기준일 이동 */
    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "week") String period,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            Model model) {
        LocalDate today = LocalDate.now(clock);
        LocalDate target = (date != null) ? date : today;
        // 기간 계산은 StatsPeriod 하나가 안다 - 여기서 다시 나누면 비교 기간과 어긋난다
        StatsPeriod stats = StatsPeriod.of(period, target);

        PeriodComparison comparison = statisticsService.compare(principal.id(), stats);
        model.addAttribute("statistics", comparison.current());
        model.addAttribute("comparison", comparison);
        model.addAttribute("streak", statisticsService.currentStreak(principal.id(), today));
        model.addAttribute("period", stats.key());
        model.addAttribute("periodLabel", stats.label());
        model.addAttribute("date", target);
        model.addAttribute("prevDate", stats.previous().from());
        model.addAttribute("nextDate", stats.next().from());
        model.addAttribute("today", today);
        return "stats/dashboard";
    }
}
