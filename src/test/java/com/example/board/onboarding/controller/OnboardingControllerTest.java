package com.example.board.onboarding.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
import com.example.board.onboarding.domain.OnboardingProgress;
import com.example.board.onboarding.service.OnboardingService;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class,
        OnboardingControllerTest.FixedClockConfig.class})
class OnboardingControllerTest {

    private static final long MEMBER_ID = 1L;

    @TestConfiguration
    static class FixedClockConfig {
        static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean OnboardingService onboardingService;
    @MockBean DdayService ddayService;
    @MockBean PlanService planService;
    @MockBean TokenService tokenService;

    /**
     * 호출 횟수를 확인하는 테스트가 있으므로 매번 깨끗한 상태에서 시작한다.
     * 컨텍스트가 재사용되면 @MockBean 에 앞 테스트의 호출 기록이 남아
     * 실행 순서에 따라 결과가 달라진다.
     */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(onboardingService, ddayService, planService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "동주"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private void givenProgress(boolean dday, boolean plan) {
        given(onboardingService.progress(eq(MEMBER_ID), any()))
                .willReturn(new OnboardingProgress(dday, plan));
    }

    @Nested
    @DisplayName("화면")
    class View {

        @Test
        @DisplayName("GET /onboarding - 아직 아무것도 없으면 1단계를 보여 준다")
        void firstStep() throws Exception {
            givenProgress(false, false);

            mockMvc.perform(get("/onboarding").with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("onboarding/onboarding"))
                    .andExpect(model().attributeExists("progress", "ddayForm", "planForm"));
        }

        @Test
        @DisplayName("GET /onboarding - 마지막 단계에서는 눌러 볼 오늘 계획을 함께 넘긴다")
        void lastStepLoadsPlans() throws Exception {
            givenProgress(true, true);
            given(planService.findDaily(FixedClockConfig.TODAY, MEMBER_ID)).willReturn(List.of());

            mockMvc.perform(get("/onboarding").with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("todayPlans"));
        }

        @Test
        @DisplayName("GET /onboarding - 앞 단계에서는 계획을 조회하지 않는다")
        void earlyStepSkipsPlanLookup() throws Exception {
            givenProgress(true, false);

            mockMvc.perform(get("/onboarding").with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeDoesNotExist("todayPlans"));

            then(planService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("GET /onboarding - 비로그인은 로그인 화면으로 보낸다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get("/onboarding"))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("단계 진행")
    class Steps {

        @Test
        @DisplayName("POST /onboarding/dday - 목표일을 만들고 다음 단계로 돌아온다")
        void createsDday() throws Exception {
            mockMvc.perform(post("/onboarding/dday")
                            .param("title", "정보처리기사 실기")
                            .param("targetDate", "2026-09-20")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/onboarding"));

            then(ddayService).should().create(any(DdayForm.class), eq(MEMBER_ID));
        }

        @Test
        @DisplayName("POST /onboarding/dday - 제목이 비면 만들지 않고 화면을 다시 보여 준다")
        void ddayValidation() throws Exception {
            givenProgress(false, false);

            mockMvc.perform(post("/onboarding/dday")
                            .param("title", "")
                            .param("targetDate", "2026-09-20")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("onboarding/onboarding"))
                    .andExpect(model().attributeHasFieldErrors("ddayForm", "title"));

            then(ddayService).should(never()).create(any(), any());
        }

        @Test
        @DisplayName("POST /onboarding/plan - 오늘 계획을 만들고 다음 단계로 돌아온다")
        void createsPlan() throws Exception {
            mockMvc.perform(post("/onboarding/plan")
                            .param("title", "백준 DP 3문제")
                            .param("category", "CODING_TEST")
                            .param("planDate", "2026-08-10")
                            .param("repeatType", "NONE")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/onboarding"));

            then(planService).should().create(any(PlanForm.class), eq(MEMBER_ID));
        }

        @Test
        @DisplayName("POST /onboarding/finish - 마쳤다고 기록하고 홈으로 보낸다 (건너뛰기도 같은 경로다)")
        void finish() throws Exception {
            mockMvc.perform(post("/onboarding/finish").with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));

            // 건너뛰기 버튼도 이 엔드포인트를 쓴다 - 다시 붙잡지 않도록 완료로 남긴다
            then(onboardingService).should().complete(MEMBER_ID);
        }

        @Test
        @DisplayName("POST /onboarding/finish - CSRF 토큰이 없으면 거부한다")
        void requiresCsrf() throws Exception {
            mockMvc.perform(post("/onboarding/finish").with(memberAuth()))
                    .andExpect(status().isForbidden());

            then(onboardingService).should(never()).complete(any());
        }
    }
}
