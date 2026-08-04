package com.example.board.auth.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.dto.LoginForm;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.service.TokenService;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final TokenService tokenService;
    private final AuthCookies authCookies;

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String redirect, Model model) {
        model.addAttribute("loginForm", new LoginForm());
        model.addAttribute("redirect", redirect);
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm,
                        BindingResult bindingResult,
                        @RequestParam(required = false) String redirect,
                        HttpServletResponse response,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("redirect", redirect);
            return "auth/login";
        }
        Member member;
        try {
            member = memberService.authenticate(loginForm.getLoginId(), loginForm.getPassword());
        } catch (LoginFailedException e) {
            bindingResult.reject("loginFailed", e.getMessage());
            model.addAttribute("redirect", redirect);
            return "auth/login";
        }

        authCookies.write(response, tokenService.issueFor(member));
        return "redirect:" + safeRedirect(redirect);
    }

    /** 로그아웃 - 리프레시 토큰을 DB 에서 폐기해 즉시 무효화하고 쿠키를 지운다 */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        authCookies.readRefreshToken(request).ifPresent(tokenService::revoke);
        authCookies.clear(response);
        redirectAttributes.addFlashAttribute("message", "로그아웃되었습니다.");
        return "redirect:/login";
    }

    /** 오픈 리다이렉트 방지 - 내부 경로만 허용 */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()
                || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return "/";
        }
        return redirect;
    }
}
