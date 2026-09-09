package com.example.board.retro.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.service.RetrospectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RetrospectiveController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class RetrospectiveControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean RetrospectiveService retrospectiveService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(retrospectiveService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @Test
    @DisplayName("POST /retros - 하루 회고를 저장하고 그날 일간 뷰로 돌아간다")
    void writeDaily() throws Exception {
        mockMvc.perform(post("/retros").with(csrf()).with(memberAuth())
                        .param("type", "DAILY")
                        .param("date", "2026-08-12")
                        .param("content", "오전에 집중이 잘 됐다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-08-12"));

        then(retrospectiveService).should()
                .write(MEMBER_ID, RetroType.DAILY, LocalDate.of(2026, 8, 12), "오전에 집중이 잘 됐다");
    }

    @Test
    @DisplayName("POST /retros - 주간 회고는 어느 요일에 써도 그 주 월요일 뷰로 돌아간다")
    void writeWeeklyRedirectsToMonday() throws Exception {
        mockMvc.perform(post("/retros").with(csrf()).with(memberAuth())
                        .param("type", "WEEKLY")
                        .param("date", "2026-08-12")   // 수요일
                        .param("content", "계획을 과하게 잡았다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/weekly?date=2026-08-10"));
    }

    @Test
    @DisplayName("POST /retros - 빈 회고는 저장하지 않고 안내만 돌려준다 (에러 페이지로 튕기지 않는다)")
    void blankContentReturnsMessage() throws Exception {
        mockMvc.perform(post("/retros").with(csrf()).with(memberAuth())
                        .param("type", "DAILY")
                        .param("date", "2026-08-12")
                        .param("content", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-08-12"))
                .andExpect(flash().attributeExists("retroError"));

        then(retrospectiveService).should(never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /retros - 길이 제한을 넘겨도 안내만 돌려준다")
    void tooLongContentReturnsMessage() throws Exception {
        mockMvc.perform(post("/retros").with(csrf()).with(memberAuth())
                        .param("type", "DAILY")
                        .param("date", "2026-08-12")
                        .param("content", "가".repeat(201)))
                .andExpect(flash().attributeExists("retroError"));

        then(retrospectiveService).should(never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /retros/{id}/delete - 지운 뒤 쓰던 화면으로 돌아간다")
    void deleteRetro() throws Exception {
        mockMvc.perform(post("/retros/5/delete").with(csrf()).with(memberAuth())
                        .param("type", "DAILY")
                        .param("date", "2026-08-12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-08-12"));

        then(retrospectiveService).should().delete(5L, MEMBER_ID);
    }

    @Test
    @DisplayName("POST /retros - 로그인 없이는 쓸 수 없다")
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/retros").with(csrf())
                        .param("type", "DAILY")
                        .param("date", "2026-08-12")
                        .param("content", "몰래 쓰기"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));

        then(retrospectiveService).should(never()).write(any(), any(), any(), any());
    }
}
