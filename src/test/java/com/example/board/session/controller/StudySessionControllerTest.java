package com.example.board.session.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import com.example.board.session.service.StudySessionService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudySessionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class,
        StudySessionControllerTest.FixedClockConfig.class})
class StudySessionControllerTest {

    /** 경과 시간 검증이 흔들리지 않도록 시각을 고정한다 */
    @TestConfiguration
    static class FixedClockConfig {
        static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 9, 30);

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean StudySessionService studySessionService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(studySessionService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(1L, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private StudySession session(LocalDateTime startedAt) {
        Member owner = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(owner, "id", 1L);
        StudySession session = StudySession.start(owner, null, PlanCategory.CODING_TEST, startedAt);
        ReflectionTestUtils.setField(session, "id", 100L);
        return session;
    }

    @Test
    @DisplayName("GET /sessions/current - 진행 중이면 경과 시간과 함께 반환한다")
    void currentRunning() throws Exception {
        given(studySessionService.findRunning(1L))
                .willReturn(Optional.of(session(FixedClockConfig.NOW.minusMinutes(25))));

        mockMvc.perform(get("/sessions/current").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.category").value("코딩테스트"))
                .andExpect(jsonPath("$.elapsedSeconds").value(1500));
    }

    @Test
    @DisplayName("GET /sessions/current - 진행 중인 세션이 없으면 204 로 응답한다")
    void currentIdle() throws Exception {
        given(studySessionService.findRunning(1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/sessions/current").with(memberAuth()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /sessions/current - 비로그인은 로그인 페이지로 보낸다")
    void currentRequiresLogin() throws Exception {
        mockMvc.perform(get("/sessions/current"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /sessions/start - 계획 없이 분류만으로 시작할 수 있다")
    void startWithoutPlan() throws Exception {
        given(studySessionService.start(eq(1L), eq(null), eq(PlanCategory.INTERVIEW), any()))
                .willReturn(session(FixedClockConfig.NOW));

        mockMvc.perform(post("/sessions/start").param("category", "INTERVIEW")
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elapsedSeconds").value(0));
    }

    @Test
    @DisplayName("POST /sessions/start - 이미 진행 중이면 409 로 알려 준다")
    void startConflict() throws Exception {
        given(studySessionService.start(any(), any(), any(), any()))
                .willThrow(new IllegalStateException("이미 진행 중인 학습이 있습니다. 먼저 종료해 주세요."));

        mockMvc.perform(post("/sessions/start").param("planId", "10")
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /sessions/start - CSRF 토큰이 없으면 거부한다")
    void startRequiresCsrf() throws Exception {
        mockMvc.perform(post("/sessions/start").param("category", "MAJOR").with(memberAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /sessions/{id}/stop - 기록된 학습 시간을 반환한다")
    void stop() throws Exception {
        StudySession stopped = session(FixedClockConfig.NOW.minusMinutes(45));
        stopped.stop(FixedClockConfig.NOW);
        given(studySessionService.stop(eq(100L), eq(1L), any())).willReturn(stopped);

        mockMvc.perform(post("/sessions/100/stop").with(csrf()).with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutes").value(45))
                .andExpect(jsonPath("$.abandoned").value(false));
    }

    @Test
    @DisplayName("POST /sessions/{id}/stop - 남의 세션이면 403 이다")
    void stopOthers() throws Exception {
        given(studySessionService.stop(eq(100L), eq(1L), any()))
                .willThrow(new AccessDeniedException("본인의 학습 기록만 다룰 수 있습니다."));

        mockMvc.perform(post("/sessions/100/stop").with(csrf()).with(memberAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /sessions/{id}/adjust - 보정한 구간으로 시간이 다시 계산된다")
    void adjust() throws Exception {
        StudySession adjusted = session(FixedClockConfig.NOW.minusHours(2));
        adjusted.adjust(FixedClockConfig.NOW.minusHours(2), FixedClockConfig.NOW.minusMinutes(30));
        given(studySessionService.adjust(eq(100L), eq(1L), any(), any())).willReturn(adjusted);

        mockMvc.perform(post("/sessions/100/adjust")
                        .param("startedAt", "2026-08-10T07:30:00")
                        .param("endedAt", "2026-08-10T09:00:00")
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutes").value(90));
    }

    @Test
    @DisplayName("POST /sessions/{id}/delete - 삭제하면 204 로 응답한다")
    void delete() throws Exception {
        mockMvc.perform(post("/sessions/100/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().isNoContent());

        then(studySessionService).should().delete(100L, 1L);
    }
}
