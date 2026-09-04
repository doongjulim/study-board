package com.example.board.plan.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.service.CommentService;
import com.example.board.dday.service.DdayService;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.ShareScope;
import com.example.board.plan.domain.PlanSearchCondition;
import com.example.board.plan.domain.PlanStatus;
import com.example.board.plan.service.PlanService;
import com.example.board.retro.service.RetrospectiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @MockBean RetrospectiveService retrospectiveService;
    @MockBean TokenService tokenService;
    @MockBean CommentService commentService;
    @MockBean DdayService ddayService;

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
        given(planService.findShared(eq(MEMBER_ID), any())).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/plans/shared").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared"));
    }

    // ── 공유 플랜 상세 ────────────────────────────────────────

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유된 플랜이면 상세와 댓글을 보여준다")
    void sharedDetail() throws Exception {
        Plan shared = planOwnedBy(999L);
        shared.changeShareScope(ShareScope.PUBLIC);
        given(planService.findById(2L)).willReturn(shared);
        given(planService.canView(shared, MEMBER_ID)).willReturn(true);
        given(commentService.findForPlan(eq(2L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(commentService.countForPlan(2L)).willReturn(0L);

        mockMvc.perform(get("/plans/shared/2").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared-detail"))
                .andExpect(model().attributeExists("plan", "comments", "commentForm"));
    }

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유되지 않은 타인의 플랜은 404 를 반환한다")
    void sharedDetail_notShared() throws Exception {
        Plan hidden = planOwnedBy(999L);
        given(planService.findById(2L)).willReturn(hidden);
        given(planService.canView(hidden, MEMBER_ID)).willReturn(false);

        mockMvc.perform(get("/plans/shared/2").with(memberAuth()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /plans/shared/{id} - 공유 해제된 플랜이라도 본인은 볼 수 있다")
    void sharedDetail_ownerCanSeeUnshared() throws Exception {
        Plan mine = planOwnedBy(MEMBER_ID);
        given(planService.findById(2L)).willReturn(mine);
        given(planService.canView(mine, MEMBER_ID)).willReturn(true);
        given(commentService.findForPlan(eq(2L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(commentService.countForPlan(2L)).willReturn(0L);

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

    // ── 반복 일정 ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /plans - 반복 종료일 없이 반복을 선택하면 폼으로 돌아가고 저장하지 않는다")
    void create_repeatWithoutUntil() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "매일 알고리즘")
                        .param("planDate", "2026-07-09")
                        .param("repeatType", "DAILY"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"))
                .andExpect(model().attributeHasFieldErrors("planForm", "repeatUntil"));

        then(planService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /plans - 반복 종료일이 시작일보다 빠르면 폼으로 돌아간다")
    void create_repeatUntilBeforeStart() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "매일 알고리즘")
                        .param("planDate", "2026-07-09")
                        .param("repeatType", "DAILY")
                        .param("repeatUntil", "2026-07-08"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("planForm", "repeatUntil"));

        then(planService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /plans - 상한을 넘는 기간이면 폼으로 돌아간다")
    void create_repeatTooMany() throws Exception {
        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "매일 알고리즘")
                        .param("planDate", "2026-07-09")
                        .param("repeatType", "DAILY")
                        .param("repeatUntil", "2030-07-09"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("planForm", "repeatUntil"));

        then(planService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /plans - 올바른 반복 설정이면 저장한다")
    void create_repeatValid() throws Exception {
        given(planService.create(any(), eq(MEMBER_ID))).willReturn(1L);

        mockMvc.perform(post("/plans").with(csrf()).with(memberAuth())
                        .param("title", "매일 알고리즘")
                        .param("planDate", "2026-07-09")
                        .param("repeatType", "DAILY")
                        .param("repeatUntil", "2026-07-20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().create(any(), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("POST /plans/{id}/delete-series - 반복 전체 삭제 후 해당 날짜로 이동한다")
    void deleteSeries() throws Exception {
        given(planService.findOwned(1L, MEMBER_ID)).willReturn(plan());
        given(planService.deleteSeries(1L, MEMBER_ID)).willReturn(5);

        mockMvc.perform(post("/plans/1/delete-series").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"))
                .andExpect(flash().attribute("message", "반복 일정 5건을 삭제했습니다."));
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
    @DisplayName("POST /plans/{id}/share - 범위를 바꾸고 해당 날짜로 이동한다")
    void share() throws Exception {
        given(planService.changeShareScope(1L, ShareScope.GROUP, MEMBER_ID)).willReturn(plan());

        mockMvc.perform(post("/plans/1/share").with(csrf()).with(memberAuth())
                        .param("scope", "GROUP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().changeShareScope(1L, ShareScope.GROUP, MEMBER_ID);
    }

    // ── 검색 ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /plans/search - 조건 없이도 내 일정을 보여 준다")
    void search_withoutCondition() throws Exception {
        given(planService.search(eq(MEMBER_ID), any(PlanSearchCondition.class), any()))
                .willReturn(new PageImpl<>(List.of(plan())));

        mockMvc.perform(get("/plans/search").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/search"))
                .andExpect(model().attributeExists("results", "condition", "pageBlock", "statuses"));
    }

    @Test
    @DisplayName("GET /plans/search - 넘긴 조건이 그대로 검색에 전달된다")
    void search_passesCondition() throws Exception {
        given(planService.search(eq(MEMBER_ID), any(PlanSearchCondition.class), any()))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/plans/search")
                        .param("keyword", "  알고리즘 ")
                        .param("category", "CODING_TEST")
                        .param("status", "TODO")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31")
                        .with(memberAuth()))
                .andExpect(status().isOk());

        ArgumentCaptor<PlanSearchCondition> captor = ArgumentCaptor.forClass(PlanSearchCondition.class);
        then(planService).should().search(eq(MEMBER_ID), captor.capture(), any());
        PlanSearchCondition condition = captor.getValue();
        assertThat(condition.keyword()).isEqualTo("알고리즘");
        assertThat(condition.category()).isEqualTo(PlanCategory.CODING_TEST);
        assertThat(condition.status()).isEqualTo(PlanStatus.TODO);
        assertThat(condition.from()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("GET /plans/search - 비로그인은 로그인 화면으로 보낸다")
    void search_requiresLogin() throws Exception {
        mockMvc.perform(get("/plans/search"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /plans/rollover - 남은 일정을 옮기고 그날 플래너로 돌아간다")
    void rollover() throws Exception {
        given(planService.rollover(MEMBER_ID, LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 9)))
                .willReturn(2);

        mockMvc.perform(post("/plans/rollover")
                        .param("from", "2026-07-08")
                        .param("to", "2026-07-09")
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));
    }

    // ── 한 줄 조각 ────────────────────────────────────────────
    //
    // 한 줄의 생김새가 템플릿과 JS 두 곳에 있어 어긋났던 자리다. 이제 서버가 그린 조각을
    // JS 가 받아 끼우므로, 그 조각이 실제로 그려지는지가 화면의 정확성을 좌우한다.

    @Test
    @DisplayName("GET /plans/{id}/row - 일정 한 줄 조각을 돌려준다")
    void row() throws Exception {
        Plan plan = plan();
        ReflectionTestUtils.setField(plan, "id", 5L);
        given(planService.findOwned(5L, MEMBER_ID)).willReturn(plan);

        mockMvc.perform(get("/plans/5/row").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("plan-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("자료구조 공부")))
                // JS 가 삽입 위치를 정할 때 쓰는 값이다 - 없으면 목록 순서가 어긋난다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-start=\"10:00\"")))
                // 예전에는 이 버튼들이 JS 가 만든 줄에만 빠져 있었다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("삭제")));
    }

    @Test
    @DisplayName("남의 일정 조각은 서비스가 막는다 - 화면 조각도 소유권을 따른다")
    void row_notOwner() throws Exception {
        given(planService.findOwned(5L, MEMBER_ID))
                .willThrow(new org.springframework.security.access.AccessDeniedException("본인 것이 아님"));

        mockMvc.perform(get("/plans/5/row").with(memberAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인은 조각을 받을 수 없다")
    void row_requiresLogin() throws Exception {
        mockMvc.perform(get("/plans/5/row"))
                .andExpect(status().is3xxRedirection());
    }

}
