package com.example.board.auth.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.exception.TooManyLoginAttemptsException;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.oauth.SocialLoginProviders;
import com.example.board.auth.service.LoginAttemptLimiter;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MemberService memberService;
    @MockBean TokenService tokenService;
    @MockBean LoginAttemptLimiter loginAttemptLimiter;
    /** 로그인 화면이 "설정된 소셜 제공자" 를 묻는다. 설정이 없으면 빈 목록이다 */
    @MockBean SocialLoginProviders socialLoginProviders;

    private Member member() {
        return new Member("tester1", "encoded-password", "테스터");
    }

    private TokenService.TokenPair tokenPair() {
        return new TokenService.TokenPair("access-token-value", "refresh-token-value",
                new MemberPrincipal(1L, "tester1", "테스터"));
    }

    @Test
    @DisplayName("GET /login - 로그인 폼이 200 을 반환한다")
    void loginForm() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeExists("loginForm"));
    }

    @Test
    @DisplayName("POST /login - 성공 시 액세스·리프레시 쿠키를 모두 HttpOnly 로 심는다")
    void login_success() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());
        given(tokenService.issueFor(any(Member.class))).willReturn(tokenPair());

        var result = mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("ACCESS_TOKEN=access-token-value")
                && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("REFRESH_TOKEN=refresh-token-value")
                && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("POST /login?redirect=/plans/daily - 성공 시 원래 가려던 내부 경로로 이동한다")
    void login_withRedirect() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());
        given(tokenService.issueFor(any(Member.class))).willReturn(tokenPair());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("redirect", "/plans/daily"))
                .andExpect(redirectedUrl("/plans/daily"));
    }

    @Test
    @DisplayName("POST /login - 외부 URL 리다이렉트는 홈으로 대체한다 (오픈 리다이렉트 방지)")
    void login_openRedirectBlocked() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());
        given(tokenService.issueFor(any(Member.class))).willReturn(tokenPair());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("redirect", "//evil.com/phish"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /login - 인증에 실패하면 그 출처의 실패로 기록한다")
    void login_failIsRecorded() throws Exception {
        given(memberService.authenticate(anyString(), anyString()))
                .willThrow(new LoginFailedException());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "wrong"))
                .andExpect(status().isOk());

        then(loginAttemptLimiter).should().recordFailure(anyString());
    }

    @Test
    @DisplayName("POST /login - 막혀 있으면 비밀번호를 확인조차 하지 않는다")
    void login_blockedSkipsAuthentication() throws Exception {
        willThrow(new TooManyLoginAttemptsException(600))
                .given(loginAttemptLimiter).checkNotBlocked(anyString());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().hasErrors())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        then(memberService).should(never()).authenticate(anyString(), anyString());
        then(loginAttemptLimiter).should(never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("POST /login - 성공하면 그 출처의 실패 기록을 지운다")
    void login_successClearsAttempts() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());
        given(tokenService.issueFor(any(Member.class))).willReturn(tokenPair());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection());

        then(loginAttemptLimiter).should().recordSuccess(anyString());
    }

    @Test
    @DisplayName("POST /login - 프록시 뒤에서는 X-Forwarded-For 의 원 클라이언트를 기준으로 센다")
    void login_usesForwardedClientIp() throws Exception {
        given(memberService.authenticate(anyString(), anyString()))
                .willThrow(new LoginFailedException());

        mockMvc.perform(post("/login").with(csrf())
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .param("loginId", "tester1")
                        .param("password", "wrong"))
                .andExpect(status().isOk());

        then(loginAttemptLimiter).should().recordFailure("203.0.113.7");
    }

    @Test
    @DisplayName("POST /login - 인증 실패 시 폼으로 돌아가고 토큰을 발급하지 않는다")
    void login_fail() throws Exception {
        given(memberService.authenticate(anyString(), anyString()))
                .willThrow(new LoginFailedException());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().hasErrors())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        then(tokenService).should(never()).issueFor(any());
    }

    @Test
    @DisplayName("POST /logout - 리프레시 토큰을 폐기하고 두 쿠키를 즉시 만료시킨다")
    void logout() throws Exception {
        var result = mockMvc.perform(post("/logout").with(csrf())
                        .cookie(new Cookie("REFRESH_TOKEN", "refresh-token-value")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        then(tokenService).should().revoke("refresh-token-value");

        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("ACCESS_TOKEN=") && cookie.contains("Max-Age=0"));
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("REFRESH_TOKEN=") && cookie.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("POST /logout - 리프레시 쿠키가 없어도 정상 처리한다")
    void logout_withoutCookie() throws Exception {
        var result = mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("ACCESS_TOKEN=") && cookie.contains("Max-Age=0"));
        then(tokenService).should(never()).revoke(anyString());
    }

    @Test
    @DisplayName("만료된 액세스 토큰이라도 리프레시 쿠키가 살아 있으면 필터가 자동 재발급한다")
    void filterReissuesWithRefreshToken() throws Exception {
        given(tokenService.refresh("refresh-token-value")).willReturn(Optional.of(tokenPair()));

        var result = mockMvc.perform(get("/login")
                        .cookie(new Cookie("REFRESH_TOKEN", "refresh-token-value")))
                .andExpect(status().isOk())
                .andReturn();

        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(cookie -> cookie.contains("ACCESS_TOKEN=access-token-value"));
    }
}
