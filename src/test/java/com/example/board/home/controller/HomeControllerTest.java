package com.example.board.home.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.home.dto.DashboardView;
import com.example.board.home.service.DashboardAssembler;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import com.example.board.stats.domain.StudyStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class,
        HomeControllerTest.FixedClockConfig.class})
class HomeControllerTest {

    @TestConfiguration
    static class FixedClockConfig {
        static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean DashboardAssembler dashboardAssembler;
    @MockBean MemberService memberService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(dashboardAssembler, memberService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(1L, "tester1", "동주"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private Member onboardedMember() {
        Member member = new Member("tester1", "encoded-password", "동주");
        member.completeOnboarding(LocalDateTime.of(2026, 8, 1, 9, 0));
        return member;
    }

    private DashboardView emptyDashboard() {
        LocalDate today = FixedClockConfig.TODAY;
        return DashboardView.of("동주", today, List.of(),
                StudyStatistics.of(List.of(), today, today),
                StudyStatistics.of(List.of(), today.minusDays(6), today),
                List.of(), 0, Member.DEFAULT_DAILY_GOAL_MINUTES);
    }

    @Test
    @DisplayName("GET / - 비로그인에게는 소개 화면을 보여 준다 (로그인으로 튕기지 않는다)")
    void anonymousSeesLanding() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/landing"));

        then(dashboardAssembler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET / - 첫 사용 안내를 마치지 않았으면 온보딩으로 보낸다")
    void notOnboardedGoesToOnboarding() throws Exception {
        given(memberService.findActive(1L))
                .willReturn(new Member("tester1", "encoded-password", "동주"));

        mockMvc.perform(get("/").with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"));

        then(dashboardAssembler).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET / - 안내를 마친 회원에게는 대시보드를 보여 준다")
    void dashboardForOnboardedMember() throws Exception {
        given(memberService.findActive(1L)).willReturn(onboardedMember());
        given(dashboardAssembler.assemble(eq(1L), eq("동주"), any())).willReturn(emptyDashboard());

        mockMvc.perform(get("/").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("home/dashboard"))
                .andExpect(model().attributeExists("dashboard"));
    }

    @Test
    @DisplayName("GET / - 오늘 날짜는 고정된 Clock 에서 온다")
    void usesInjectedClock() throws Exception {
        given(memberService.findActive(1L)).willReturn(onboardedMember());
        given(dashboardAssembler.assemble(any(), any(), any())).willReturn(emptyDashboard());

        mockMvc.perform(get("/").with(memberAuth())).andExpect(status().isOk());

        then(dashboardAssembler).should().assemble(1L, "동주", FixedClockConfig.TODAY);
    }
}
