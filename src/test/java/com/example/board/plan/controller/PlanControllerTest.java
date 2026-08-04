package com.example.board.plan.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.service.CommentService;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.service.PlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class PlanControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean PlanService planService;
    @MockBean TokenService tokenService;
    @MockBean CommentService commentService;

    private Plan plan() {
        return planOwnedBy(MEMBER_ID);
    }

    private Plan planOwnedBy(long authorId) {
        Member author = new Member("tester" + authorId, "encoded-password", "동주");
        ReflectionTestUtils.setField(author, "id", authorId);
        return new Plan("자료구조 공부", "스택, 큐 복습", author, PlanCategory.CODING_TEST,
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    /** 로그인한 회원(id=1)으로 요청을 보낸다 */
    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    // ── 접근 제어 ─────────────────────────────────────────────

    @Test
    @DisplayName("비로그인으로 플래너에 접근하면 로그인 페이지로 리다이렉트한다")
    void daily_requiresLogin() throws Exception {
        mockMvc.perform(get("/plans/daily"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    // ── 조회 화면 ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /plans - 일간 뷰로 리다이렉트한다")
    void home() throws Exception {
        mockMvc.perform(get("/plans").with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily"));
    }

    @Test
    @DisplayName("GET /plans/daily - 로그인 회원의 일간 뷰가 200 을 반환한다")
    void daily() throws Exception {
        given(planService.findDaily(any(LocalDate.class), eq(MEMBER_ID))).willReturn(List.of());

        mockMvc.perform(get("/plans/daily").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/daily"))
                .andExpect(model().attributeExists("date", "plans", "prevDate", "nextDate"));

        then(planService).should().findDaily(any(LocalDate.class), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("GET /plans/daily?date=2026-07-09 - 지정한 날짜가 모델에 담긴다")
    void daily_withDate() throws Exception {
        given(planService.findDaily(LocalDate.of(2026, 7, 9), MEMBER_ID)).willReturn(List.of());

        mockMvc.perform(get("/plans/daily").param("date", "2026-07-09").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("date", LocalDate.of(2026, 7, 9)));
    }

    @Test
    @DisplayName("GET /plans/weekly - 주간 뷰는 항상 7일을 보여준다")
    void weekly() throws Exception {
        given(planService.findWeek(any(LocalDate.class), eq(MEMBER_ID))).willReturn(List.of());

        mockMvc.perform(get("/plans/weekly").param("date", "2026-07-08").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/weekly"))
                .andExpect(model().attribute("weekDays", hasSize(7)))
                .andExpect(model().attribute("weekStart", LocalDate.of(2026, 7, 6)));
    }

    @Test
    @DisplayName("GET /plans/monthly - 월간 캘린더가 200 을 반환한다")
    void monthly() throws Exception {
        given(planService.findMonth(any(), eq(MEMBER_ID))).willReturn(List.of());

        mockMvc.perform(get("/plans/monthly").param("month", "2026-07").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/monthly"))
                .andExpect(model().attributeExists("weeks", "plansByDate"));
    }

    @Test
    @DisplayName("GET /plans/shared - 공유 플랜 목록이 200 을 반환한다")
    void shared() throws Exception {
        given(planService.findShared(any())).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/plans/shared").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared"));
    }

    // ── 공유 플랜 상세 ────────────────────────────────────────

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유된 플랜이면 상세와 댓글을 보여준다")
    void sharedDetail() throws Exception {
        Plan shared = planOwnedBy(999L);
        shared.toggleShared();
        given(planService.findById(2L)).willReturn(shared);
        given(commentService.findForPlan(2L)).willReturn(List.of());

        mockMvc.perform(get("/plans/shared/2").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared-detail"))
                .andExpect(model().attributeExists("plan", "comments", "commentForm"));
    }

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유되지 않은 타인의 플랜은 404 를 반환한다")
    void sharedDetail_notShared() throws Exception {
        given(planService.findById(2L)).willReturn(planOwnedBy(999L));

        mockMvc.perform(get("/plans/shared/2").with(memberAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유 해제된 플랜이라도 본인은 볼 수 있다")
    void sharedDetail_ownerCanSeeUnshared() throws Exception {
        given(planService.findById(2L)).willReturn(planOwnedBy(MEMBER_ID));
        given(commentService.findForPlan(2L)).willReturn(List.of());

        mockMvc.perform(get("/plans/shared/2").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared-detail"));
    }

    // ── 등록 / 수정 / 삭제 ────────────────────────────────────

    @Test
    @DisplayName("GET /plans/new - 작성 폼이 200 을 반환한다")
    void createForm() throws Exception {
        mockMvc.perform(get("/plans/new").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"))
                .andExpect(model().attributeExists("planForm"));
    }

    @Test
    @DisplayName("POST /plans - 유효한 입력이면 저장 후 해당 날짜 일간 뷰로 이동한다")
    void create() throws Exception {
        given(planService.create(any(), eq(MEMBER_ID))).willReturn(1L);

        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "자료구조 공부")
                        .param("planDate", "2026-07-09")
                        .param("startTime", "10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().create(any(), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("POST /plans - 제목이 비면 폼으로 돌아가고 저장하지 않는다")
    void create_invalid() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "")
                        .param("planDate", "2026-07-09"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"));

        then(planService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /plans - 종료 시간이 시작 시간보다 빠르면 폼으로 돌아간다")
    void create_invalidTimeRange() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "자료구조 공부")
                        .param("planDate", "2026-07-09")
                        .param("startTime", "12:00")
                        .param("endTime", "10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"));

        then(planService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /plans/{id}/delete - 삭제 후 해당 날짜 일간 뷰로 이동한다")
    void deletePlan() throws Exception {
        given(planService.findOwned(1L, MEMBER_ID)).willReturn(plan());

        mockMvc.perform(post("/plans/1/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().delete(1L, MEMBER_ID);
    }

    // ── 상태 변경 ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /plans/{id}/toggle - 완료 전환 후 해당 날짜로 이동한다")
    void toggle() throws Exception {
        given(planService.toggleCompleted(1L, MEMBER_ID)).willReturn(plan());

        mockMvc.perform(post("/plans/1/toggle").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));
    }

    @Test
    @DisplayName("POST /plans/{id}/share - 공유 전환 후 해당 날짜로 이동한다")
    void share() throws Exception {
        given(planService.toggleShared(1L, MEMBER_ID)).willReturn(plan());

        mockMvc.perform(post("/plans/1/share").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));
    }
}
