package com.example.board.retro.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.service.RetrospectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * 회고 쓰기·지우기.
 *
 * <p>회고는 계획을 보던 화면에서 바로 적는 것이라 전용 화면을 두지 않는다.
 * 저장 후에는 쓰던 화면으로 되돌려 보낸다.</p>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/retros")
public class RetrospectiveController {

    private final RetrospectiveService retrospectiveService;

    @PostMapping
    public String write(@RequestParam RetroType type,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        @RequestParam String content,
                        @AuthenticationPrincipal MemberPrincipal principal,
                        RedirectAttributes redirectAttributes) {
        // 길이·공백 규칙은 RetroType 한 곳에 있다. 여기서 미리 걸러 에러 페이지 대신 안내로 돌려보낸다
        if (!type.fits(content)) {
            redirectAttributes.addFlashAttribute("retroError",
                    "%s는 1자 이상 %d자 이하로 입력하세요.".formatted(type.getLabel(), type.getMaxLength()));
            return redirectTo(type, date);
        }
        retrospectiveService.write(principal.id(), type, date, content);
        redirectAttributes.addFlashAttribute("message", type.getLabel() + "를 저장했습니다.");
        return redirectTo(type, date);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam RetroType type,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        retrospectiveService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", type.getLabel() + "를 지웠습니다.");
        return redirectTo(type, date);
    }

    /** 회고를 쓴 화면으로 돌려보낸다 (하루는 일간 뷰, 주간은 주간 뷰) */
    private static String redirectTo(RetroType type, LocalDate date) {
        String view = (type == RetroType.WEEKLY) ? "weekly" : "daily";
        return "redirect:/plans/%s?date=%s".formatted(view, type.anchorDate(date));
    }
}
