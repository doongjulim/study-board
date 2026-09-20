package com.example.board.application.controller;

import com.example.board.application.domain.ApplicationResult;
import com.example.board.application.domain.ApplicationStage;
import com.example.board.application.domain.JobApplication;
import com.example.board.application.dto.JobApplicationForm;
import com.example.board.application.service.JobApplicationService;
import com.example.board.auth.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 지원 현황 - 어느 회사에, 지금 어느 단계로, 언제까지.
 *
 * <p>목록과 등록 폼을 한 화면에 둔다(D-Day 와 같은 방식이다). 지원은 공고를 보다가 바로 적는
 * 것이라, 적으러 다른 화면으로 나가야 하면 적지 않게 된다.</p>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/applications")
public class JobApplicationController {

    // @ModelAttribute 의 이름을 적어 두는 이유: 생략하면 타입 이름에서 뽑혀
    // jobApplicationForm 이 되는데, 화면은 applicationForm 으로 찾는다.
    // 이름이 어긋나면 검증 실패로 화면을 다시 그릴 때만 터진다 - 정상 경로에서는 보이지 않는다.

    private final JobApplicationService applicationService;
    /** "오늘" 은 기계가 아니라 서비스의 시간대가 정한다 ({@link com.example.board.common.time.ServiceZone}) */
    private final Clock clock;

    @GetMapping
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        prepare(model, principal.id());
        if (!model.containsAttribute("applicationForm")) {
            model.addAttribute("applicationForm", new JobApplicationForm());
        }
        return "applications/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("applicationForm") JobApplicationForm applicationForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepare(model, principal.id());
            return "applications/list";
        }
        applicationService.create(applicationForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "지원 현황을 추가했습니다.");
        return "redirect:/applications";
    }

    /** 수정 폼 - 단계·결과가 함께 바뀌므로 목록 안에서 처리하지 않고 따로 연다 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        JobApplication application = applicationService.findOwned(id, principal.id());
        model.addAttribute("applicationForm", JobApplicationForm.from(application));
        model.addAttribute("applicationId", id);
        model.addAttribute("application", application);
        model.addAttribute("today", LocalDate.now(clock));
        return "applications/form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("applicationForm") JobApplicationForm applicationForm,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("applicationId", id);
            model.addAttribute("today", LocalDate.now(clock));
            return "applications/form";
        }
        applicationService.update(id, applicationForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "지원 현황을 수정했습니다.");
        return "redirect:/applications";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        applicationService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "지원 현황을 삭제했습니다.");
        return "redirect:/applications";
    }

    /** 목록 화면이 늘 필요로 하는 것들 - 검증 실패로 화면을 다시 그릴 때도 같아야 한다 */
    private void prepare(Model model, Long memberId) {
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("applications", applicationService.findMine(memberId));
        model.addAttribute("focus", applicationService.currentFocus(memberId, today));
        model.addAttribute("today", today);
    }

    @ModelAttribute("stages")
    public ApplicationStage[] stages() {
        return ApplicationStage.values();
    }

    @ModelAttribute("results")
    public ApplicationResult[] results() {
        return ApplicationResult.values();
    }
}
