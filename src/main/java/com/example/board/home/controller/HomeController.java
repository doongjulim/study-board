package com.example.board.home.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.home.service.DashboardAssembler;
import com.example.board.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 첫 화면. 누가 왔느냐에 따라 세 갈래로 나뉜다.
 *
 * <ul>
 *   <li>처음 온 사람 → 무엇을 하는 서비스인지 보여 주는 소개 화면</li>
 *   <li>가입했지만 아직 아무것도 안 해 본 사람 → 첫 사용 안내</li>
 *   <li>쓰고 있는 사람 → 오늘 무엇을 얼마나 했는지 보여 주는 대시보드</li>
 * </ul>
 *
 * <p>예전에는 곧장 플래너로 넘겼지만, 오늘 상황이 다섯 페이지에 흩어져 있어
 * 매일 열어 볼 이유가 되지 못했다.</p>
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DashboardAssembler dashboardAssembler;
    private final MemberService memberService;
    private final Clock clock;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home/landing";
        }
        if (!memberService.findActive(principal.id()).isOnboarded()) {
            return "redirect:/onboarding";
        }
        model.addAttribute("dashboard",
                dashboardAssembler.assemble(principal.id(), principal.nickname(), LocalDate.now(clock)));
        return "home/dashboard";
    }
}
