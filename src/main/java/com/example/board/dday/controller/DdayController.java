package com.example.board.dday.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.dday.domain.Dday;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/ddays")
public class DdayController {

    private final DdayService ddayService;
    /** "오늘" 은 기계가 아니라 서비스의 시간대가 정한다 ({@link com.example.board.common.time.ServiceZone}) */
    private final Clock clock;

    /** 목록 + 등록 폼 - 항목이 적어 한 화면에서 처리한다 */
    @GetMapping
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("ddays", ddayService.findMine(principal.id()));
        model.addAttribute("today", LocalDate.now(clock));
        if (!model.containsAttribute("ddayForm")) {
            model.addAttribute("ddayForm", new DdayForm());
        }
        return "ddays/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute DdayForm ddayForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("ddays", ddayService.findMine(principal.id()));
            model.addAttribute("today", LocalDate.now(clock));
            return "ddays/list";
        }
        ddayService.create(ddayForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "D-Day 를 등록했습니다.");
        return "redirect:/ddays";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        Dday dday = ddayService.findOwned(id, principal.id());
        DdayForm form = new DdayForm();
        form.setTitle(dday.getTitle());
        form.setTargetDate(dday.getTargetDate());
        model.addAttribute("ddayForm", form);
        model.addAttribute("ddayId", id);
        return "ddays/form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute DdayForm ddayForm,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("ddayId", id);
            return "ddays/form";
        }
        ddayService.update(id, ddayForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "D-Day 를 수정했습니다.");
        return "redirect:/ddays";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        ddayService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "D-Day 를 삭제했습니다.");
        return "redirect:/ddays";
    }
}
