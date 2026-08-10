package com.example.board.home.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.home.service.DashboardAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 첫 화면.
 *
 * <p>예전에는 곧장 플래너로 넘겼지만, 오늘 무엇을 얼마나 했는지가
 * 다섯 페이지에 흩어져 있어 매일 열어 볼 이유가 되지 못했다.
 * 진행률·연속 달성·다음 할 일을 한자리에 모아 보여 준다.</p>
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DashboardAssembler dashboardAssembler;
    private final Clock clock;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            // 소개 화면(랜딩)이 생기기 전까지는 로그인으로 보낸다
            return "redirect:/login";
        }
        model.addAttribute("dashboard",
                dashboardAssembler.assemble(principal.id(), principal.nickname(), LocalDate.now(clock)));
        return "home/dashboard";
    }
}
