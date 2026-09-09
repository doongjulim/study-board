package com.example.board.plan.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanApiController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class PlanApiControllerTest {

    private static final long MEMBER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @Autowired MockMvc mockMvc;
    @MockBean PlanService planService;
    @MockBean TokenService tokenService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(planService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private Plan plan(Long id, String title, boolean completed) {
        Member author = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(author, "id", MEMBER_ID);
        Plan plan = new Plan(title, null, author, PlanCategory.CODING_TEST, TODAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        ReflectionTestUtils.setField(plan, "id", id);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    @Nested
    @DisplayName("한 줄 추가")
    class QuickAdd {

        @Test
        @DisplayName("제목과 날짜만으로 등록하고 추가된 행을 돌려준다")
        void addsWithTitleAndDate() throws Exception {
            given(planService.create(any(PlanForm.class), eq(MEMBER_ID))).willReturn(10L);
            given(planService.findOwned(10L, MEMBER_ID)).willReturn(plan(10L, "백준 DP 3문제", false));
            given(planService.findDaily(TODAY, MEMBER_ID))
                    .willReturn(List.of(plan(10L, "백준 DP 3문제", false)));

            mockMvc.perform(post("/api/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"백준 DP 3문제","planDate":"2026-08-12","category":"CODING_TEST"}
                                    """)
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.plan.id").value(10))
                    .andExpect(jsonPath("$.plan.title").value("백준 DP 3문제"))
                    .andExpect(jsonPath("$.plan.category").value("코딩테스트"))
                    .andExpect(jsonPath("$.plan.time").value("09:00 ~ 10:00"))
                    .andExpect(jsonPath("$.progress.totalCount").value(1));
        }

        @Test
        @DisplayName("제목이 비면 400 과 안내 문구를 돌려준다 (HTML 오류 페이지가 아니라)")
        void rejectsBlankTitle() throws Exception {
            mockMvc.perform(post("/api/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"","planDate":"2026-08-12"}
                                    """)
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("할 일을 입력하세요."));

            then(planService).should(never()).create(any(), any());
        }

        @Test
        @DisplayName("CSRF 토큰이 없으면 거부한다")
        void requiresCsrf() throws Exception {
            mockMvc.perform(post("/api/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"할 일\",\"planDate\":\"2026-08-12\"}")
                            .with(memberAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("비로그인은 로그인 화면으로 보낸다")
        void requiresLogin() throws Exception {
            mockMvc.perform(post("/api/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"할 일\",\"planDate\":\"2026-08-12\"}")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("완료 토글")
    class Toggle {

        @Test
        @DisplayName("바뀐 상태와 그날 진행 상황을 함께 돌려준다")
        void returnsProgress() throws Exception {
            given(planService.toggleCompleted(10L, MEMBER_ID)).willReturn(plan(10L, "할 일", true));
            given(planService.findDaily(TODAY, MEMBER_ID)).willReturn(List.of(
                    plan(10L, "할 일", true), plan(11L, "다른 일", false)));

            mockMvc.perform(post("/api/plans/10/toggle").with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.completed").value(true))
                    .andExpect(jsonPath("$.progress.completedCount").value(1))
                    .andExpect(jsonPath("$.progress.totalCount").value(2))
                    .andExpect(jsonPath("$.progress.completionRate").value(50));
        }

        @Test
        @DisplayName("남의 일정이면 403 이다")
        void rejectsOthers() throws Exception {
            given(planService.toggleCompleted(10L, MEMBER_ID))
                    .willThrow(new AccessDeniedException("본인의 플랜만 수정하거나 삭제할 수 있습니다."));

            mockMvc.perform(post("/api/plans/10/toggle").with(csrf()).with(memberAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("없는 일정이면 404 다")
        void missingPlan() throws Exception {
            given(planService.toggleCompleted(99L, MEMBER_ID))
                    .willThrow(new IllegalArgumentException("플랜이 존재하지 않습니다. id=99"));

            mockMvc.perform(post("/api/plans/99/toggle").with(csrf()).with(memberAuth()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("이월")
    class Rollover {

        @Test
        @DisplayName("옮긴 건수와 옮겨진 날의 진행 상황을 돌려준다")
        void movesUnfinished() throws Exception {
            given(planService.rollover(MEMBER_ID, TODAY.minusDays(1), TODAY)).willReturn(3);
            given(planService.findDaily(TODAY, MEMBER_ID)).willReturn(List.of(
                    plan(10L, "a", false), plan(11L, "b", false), plan(12L, "c", false)));

            mockMvc.perform(post("/api/plans/rollover")
                            .param("from", "2026-08-11")
                            .param("to", "2026-08-12")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.movedCount").value(3))
                    .andExpect(jsonPath("$.progress.remainingCount").value(3));
        }

        @Test
        @DisplayName("옮길 일정이 없으면 0 을 돌려준다")
        void nothingToMove() throws Exception {
            given(planService.rollover(MEMBER_ID, TODAY.minusDays(1), TODAY)).willReturn(0);
            given(planService.findDaily(TODAY, MEMBER_ID)).willReturn(List.of());

            mockMvc.perform(post("/api/plans/rollover")
                            .param("from", "2026-08-11")
                            .param("to", "2026-08-12")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.movedCount").value(0));
        }
    }
}
