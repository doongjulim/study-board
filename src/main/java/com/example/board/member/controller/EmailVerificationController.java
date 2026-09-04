package com.example.board.member.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.member.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 이메일 인증.
 *
 * <p>확인 링크(GET)는 <b>로그인 없이</b> 열려야 한다. 메일은 다른 기기에서 열리는 일이 흔하고,
 * 로그인부터 하라고 막으면 인증률이 떨어진다. 링크에 든 토큰이 곧 신원이다 -
 * 추측할 수 없는 값이고, 한 번 쓰면 폐기되며, 하는 일은 "이 주소가 닿는다" 를 표시하는 것뿐이다.</p>
 */
@Controller
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /** 인증 메일 보내기 (마이페이지에서) */
    @PostMapping("/me/email/verify-request")
    public String sendVerification(@AuthenticationPrincipal MemberPrincipal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            emailVerificationService.sendVerificationLink(principal.id());
            redirectAttributes.addFlashAttribute("message",
                    "인증 메일을 보냈습니다. 메일함을 확인해 주세요. (개발 모드에서는 콘솔 로그에 링크가 찍힙니다)");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/me";
    }

    /** 메일의 링크 - 로그인 없이 열린다 */
    @GetMapping("/email/verify")
    public String verify(@RequestParam(required = false) String token,
                         RedirectAttributes redirectAttributes) {
        boolean verified = emailVerificationService.verify(token);
        redirectAttributes.addFlashAttribute("message", verified
                ? "이메일 인증이 끝났습니다."
                : "링크가 만료되었거나 이미 사용되었습니다. 마이페이지에서 다시 보내 주세요.");
        // 인증만 하고 끝내지 않고 마이페이지로 보낸다 - 결과를 확인할 자리가 거기다
        return "redirect:/me";
    }
}
