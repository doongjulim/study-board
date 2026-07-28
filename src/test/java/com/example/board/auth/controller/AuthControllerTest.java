package com.example.board.auth.controller;

import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MemberService memberService;

    private Member member() {
        return new Member("tester1", "encoded-password", "테스터");
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
    @DisplayName("POST /login - 성공 시 HttpOnly 액세스 토큰 쿠키를 심고 홈으로 이동한다")
    void login_success() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("ACCESS_TOKEN="),
                                containsString("HttpOnly"),
                                containsString("SameSite=Lax"))));
    }

    @Test
    @DisplayName("POST /login?redirect=/plans/daily - 성공 시 원래 가려던 내부 경로로 이동한다")
    void login_withRedirect() throws Exception {
        given(memberService.authenticate("tester1", "password123")).willReturn(member());

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

        mockMvc.perform(post("/login").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("redirect", "//evil.com/phish"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /login - 인증 실패 시 폼으로 돌아가고 쿠키를 심지 않는다")
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
    }

    @Test
    @DisplayName("POST /logout - 쿠키를 즉시 만료시키고 로그인 페이지로 이동한다")
    void logout() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("ACCESS_TOKEN="), containsString("Max-Age=0"))));
    }
}
