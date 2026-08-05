package com.example.board.stats.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.stats.service.StudyStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StudyStatisticsService statisticsService;

    /** 학습 통계 대시보드 - 주간/월간 전환 + 기준일 이동 */
    @GetMapping
    public String dashboard(@RequestParam(defaultValue = "week") String period,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate target = (date != null) ? date : today;
        boolean monthly = "month".equals(period);

        LocalDate from = monthly ? target.withDayOfMonth(1) : target.with(DayOfWeek.MONDAY);
        LocalDate to = monthly ? target.withDayOfMonth(target.lengthOfMonth()) : from.plusDays(6);

        model.addAttribute("statistics", statisticsService.calculate(principal.id(), from, to));
        model.addAttribute("streak", statisticsService.currentStreak(principal.id(), today));
        model.addAttribute("period", monthly ? "month" : "week");
        model.addAttribute("periodLabel", monthly ? "이번 달" : "이번 주");
        model.addAttribute("date", target);
        model.addAttribute("prevDate", monthly ? from.minusMonths(1) : from.minusWeeks(1));
        model.addAttribute("nextDate", monthly ? from.plusMonths(1) : from.plusWeeks(1));
        model.addAttribute("today", today);
        return "stats/dashboard";
    }
}
