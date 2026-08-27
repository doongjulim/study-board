package com.example.board.onboarding.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
import com.example.board.onboarding.domain.OnboardingProgress;
import com.example.board.onboarding.service.OnboardingService;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 가입 직후 첫 사용 안내.
 *
 * <p>목표를 정하고(1) 오늘 할 일을 적고(2) 타이머를 눌러 보는(3) 데까지 3분 안에 데려간다.
 * 빈 화면부터 마주하면 무엇을 하는 서비스인지 알 수 없기 때문이다.</p>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final DdayService ddayService;
    private final PlanService planService;
    private final Clock clock;

    @GetMapping
    public String onboarding(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        return render(principal, model);
    }

    @PostMapping("/dday")
    public String createDday(@Valid @ModelAttribute DdayForm ddayForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return render(principal, model);
        }
        ddayService.create(ddayForm, principal.id());
        return "redirect:/onboarding";
    }

    @PostMapping("/plan")
    public String createPlan(@Valid @ModelAttribute PlanForm planForm,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return render(principal, model);
        }
        planService.create(planForm, principal.id());
        return "redirect:/onboarding";
    }

    /** 끝까지 봤든 건너뛰었든 같은 처리 - 다시 붙잡지 않는다 */
    @PostMapping("/finish")
    public String finish(@AuthenticationPrincipal MemberPrincipal principal) {
        onboardingService.complete(principal.id());
        return "redirect:/";
    }

    /** 폼의 분류 선택지 */
    @ModelAttribute("categories")
    public PlanCategory[] categories() {
        return PlanCategory.values();
    }

    private String render(MemberPrincipal principal, Model model) {
        LocalDate today = LocalDate.now(clock);
        OnboardingProgress progress = onboardingService.progress(principal.id(), today);

        model.addAttribute("progress", progress);
        model.addAttribute("today", today);
        model.addAttribute("nickname", principal.nickname());
        if (!model.containsAttribute("ddayForm")) {
            model.addAttribute("ddayForm", new DdayForm());
        }
        if (!model.containsAttribute("planForm")) {
            model.addAttribute("planForm", defaultPlanForm(today));
        }
        // 마지막 단계에서 방금 만든 계획으로 타이머를 눌러 볼 수 있게 한다
        if (progress.isLastStep()) {
            model.addAttribute("todayPlans", planService.findDaily(today, principal.id()));
        }
        return "onboarding/onboarding";
    }

    private PlanForm defaultPlanForm(LocalDate today) {
        PlanForm form = new PlanForm();
        form.setPlanDate(today);
        return form;
    }
}
