package com.example.board.auth.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.dto.LoginForm;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.exception.TooManyLoginAttemptsException;
import com.example.board.auth.service.LoginAttemptLimiter;
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
    private final LoginAttemptLimiter loginAttemptLimiter;

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
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("redirect", redirect);
            return "auth/login";
        }

        String attemptKey = clientIp(request);
        Member member;
        try {
            // 막혀 있으면 비밀번호를 확인조차 하지 않는다 - 확인 자체가 공격자에게 주는 시도 기회다
            loginAttemptLimiter.checkNotBlocked(attemptKey);
            member = memberService.authenticate(loginForm.getLoginId(), loginForm.getPassword());
        } catch (LoginFailedException e) {
            loginAttemptLimiter.recordFailure(attemptKey);
            bindingResult.reject("loginFailed", e.getMessage());
            model.addAttribute("redirect", redirect);
            return "auth/login";
        } catch (TooManyLoginAttemptsException e) {
            bindingResult.reject("tooManyAttempts", e.getMessage());
            model.addAttribute("redirect", redirect);
            return "auth/login";
        }

        loginAttemptLimiter.recordSuccess(attemptKey);
        authCookies.write(response, tokenService.issueFor(member));
        return "redirect:" + safeRedirect(redirect);
    }

    /**
     * 시도를 세는 기준이 되는 요청 출처.
     *
     * <p>프록시·로드밸런서 뒤에 있으면 모든 요청이 프록시 주소로 보여 전체가 한 덩어리로 묶인다.
     * 그래서 X-Forwarded-For 의 <b>첫 번째</b> 값(원 클라이언트)을 쓴다.
     * 이 헤더는 클라이언트가 위조할 수 있으므로, 앞단에서 헤더를 덮어쓰도록 설정한 경우에만 믿을 수 있다 -
     * 위조하면 시도 제한을 우회할 수 있을 뿐 남의 계정을 잠글 수는 없어, 피해가 자기 자신에게만 남는다.</p>
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
