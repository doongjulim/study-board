package com.example.board.auth.controller;

import com.example.board.auth.dto.LoginForm;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
    private final JwtTokenProvider tokenProvider;

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

        String token = tokenProvider.createToken(member.getId(), member.getLoginId(), member.getNickname());
        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie(token, tokenProvider.getValiditySeconds()));
        return "redirect:" + safeRedirect(redirect);
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response, RedirectAttributes redirectAttributes) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie("", 0)); // 즉시 만료
        redirectAttributes.addFlashAttribute("message", "로그아웃되었습니다.");
        return "redirect:/login";
    }

    private String accessTokenCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
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
