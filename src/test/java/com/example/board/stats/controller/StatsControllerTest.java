package com.example.board.stats.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.service.StudyStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class StatsControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean StudyStatisticsService statisticsService;
    @MockBean TokenService tokenService;

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private StudyStatistics emptyStatistics(LocalDate from, LocalDate to) {
        return StudyStatistics.of(List.of(), from, to);
    }

    @Test
    @DisplayName("GET /stats - 기본은 이번 주(월~일) 범위로 조회한다")
    void dashboard_defaultsToWeek() throws Exception {
        given(statisticsService.calculate(eq(MEMBER_ID), any(), any()))
                .willReturn(emptyStatistics(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9)));

        mockMvc.perform(get("/stats").param("date", "2026-08-05").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("stats/dashboard"))
                .andExpect(model().attributeExists("statistics", "streak"))
                .andExpect(model().attribute("period", "week"));

        then(statisticsService).should().calculate(MEMBER_ID,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9));
    }

    @Test
    @DisplayName("GET /stats?period=month - 해당 월 1일~말일 범위로 조회한다")
    void dashboard_month() throws Exception {
        given(statisticsService.calculate(eq(MEMBER_ID), any(), any()))
                .willReturn(emptyStatistics(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        mockMvc.perform(get("/stats").param("period", "month").param("date", "2026-08-05")
                        .with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("period", "month"));

        then(statisticsService).should().calculate(MEMBER_ID,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("연속 달성일은 오늘 기준으로 계산해 모델에 담는다")
    void dashboard_streak() throws Exception {
        given(statisticsService.calculate(eq(MEMBER_ID), any(), any()))
                .willReturn(emptyStatistics(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9)));
        given(statisticsService.currentStreak(eq(MEMBER_ID), any())).willReturn(5);

        mockMvc.perform(get("/stats").with(memberAuth()))
                .andExpect(model().attribute("streak", 5));
    }

    @Test
    @DisplayName("비로그인으로 통계에 접근하면 로그인 페이지로 리다이렉트한다")
    void dashboard_requiresLogin() throws Exception {
        mockMvc.perform(get("/stats"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }
}
