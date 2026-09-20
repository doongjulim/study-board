package com.example.board.plan.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.service.CommentService;
import com.example.board.dday.service.DdayService;
import com.example.board.application.service.JobApplicationService;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.support.TestClockConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.EstimatePreset;
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
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class, TestClockConfig.class})
class PlanControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean PlanService planService;
    @MockBean RetrospectiveService retrospectiveService;
    @MockBean TokenService tokenService;
    @MockBean CommentService commentService;
    @MockBean DdayService ddayService;
    @MockBean JobApplicationService applicationService;

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
    @DisplayName("검색 결과의 일정도 일간 뷰와 똑같이 다룰 수 있다")
    void search_rowsAreInteractive() throws Exception {
        // 예전에는 이 화면만 줄을 손으로 그려서, 체크 상자가 <span> 이라 눌러도 완료가 되지 않았다.
        // 모양은 일간 뷰와 똑같았으므로 사용자는 자기 조작이 실패했다고 느꼈다
        Plan plan = plan();
        ReflectionTestUtils.setField(plan, "id", 5L);
        given(planService.search(eq(MEMBER_ID), any(PlanSearchCondition.class), any()))
                .willReturn(new PageImpl<>(List.of(plan)));

        mockMvc.perform(get("/plans/search").with(memberAuth()))
                .andExpect(status().isOk())
                // 완료 토글 - 일간 뷰와 같은 폼이다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/plans/5/toggle")))
                // 삭제·공유 범위도 여기서 바로 할 수 있어야 한다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/plans/5/delete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/plans/5/share")));
    }

    @Test
    @DisplayName("여러 날짜가 섞인 목록에만 날짜가 붙는다")
    void search_showsDateButDailyDoesNot() throws Exception {
        Plan plan = plan();
        ReflectionTestUtils.setField(plan, "id", 5L);
        given(planService.search(eq(MEMBER_ID), any(PlanSearchCondition.class), any()))
                .willReturn(new PageImpl<>(List.of(plan)));
        given(planService.findDaily(any(), eq(MEMBER_ID))).willReturn(List.of(plan));

        // 검색 결과는 날짜가 섞여 있으니 줄마다 날짜가 필요하다
        mockMvc.perform(get("/plans/search").with(memberAuth()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("plan-date")));
        // 일간 뷰는 화면 제목이 이미 그날이라 줄마다 반복할 이유가 없다
        mockMvc.perform(get("/plans/daily").with(memberAuth()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("plan-date"))));
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

    // ── 예상 소요 시간 ────────────────────────────────────────
    //
    // 계획 시간이 시각에서만 나오던 동안, 주 입력 경로인 한 줄 추가로 적은 일정은 계획 시간이 0 이었고
    // 통계는 0 이면 실행률을 감췄다. 화면에서 이 입력이 사라지면 같은 상태로 조용히 돌아간다.

    private Plan planWithEstimate(Integer estimatedMinutes, LocalTime start, LocalTime end) {
        Member author = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(author, "id", MEMBER_ID);
        Plan plan = new Plan("자료구조 공부", null, author, PlanCategory.CODING_TEST,
                LocalDate.of(2026, 7, 9), start, end, estimatedMinutes);
        ReflectionTestUtils.setField(plan, "id", 5L);
        return plan;
    }

    @Test
    @DisplayName("일간 뷰의 한 줄 추가에 예상 소요 시간 칩이 모두 그려진다 - 클릭 한 번으로 끝나야 채워진다")
    void daily_rendersEstimateChips() throws Exception {
        given(planService.findDaily(any(LocalDate.class), eq(MEMBER_ID))).willReturn(List.of());

        String html = mockMvc.perform(get("/plans/daily").with(memberAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("name=\"estimatedMinutes\"");
        for (EstimatePreset preset : EstimatePreset.values()) {
            assertThat(html)
                    .as("칩 %s 가 없다", preset.getLabel())
                    .contains("value=\"" + preset.getMinutes() + "\"")
                    .contains(preset.getLabel());
        }
    }

    @Test
    @DisplayName("시각 없이 예상 소요 시간만 적은 일정은 줄에 '예상 1시간' 이 함께 나온다")
    void row_showsEstimate() throws Exception {
        given(planService.findOwned(5L, MEMBER_ID)).willReturn(planWithEstimate(60, null, null));

        mockMvc.perform(get("/plans/5/row").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("plan-estimate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("예상 1시간")));
    }

    @Test
    @DisplayName("시작·종료 시각이 둘 다 있으면 예상값은 줄에 나오지 않는다 - 계획 시간으로 쓰이지 않는 숫자다")
    void row_hidesEstimateWhenTimeRangeWins() throws Exception {
        given(planService.findOwned(5L, MEMBER_ID))
                .willReturn(planWithEstimate(30, LocalTime.of(10, 0), LocalTime.of(12, 0)));

        mockMvc.perform(get("/plans/5/row").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("10:00 ~ 12:00")))
                // 설명 주석에도 '예상' 이 들어 있으므로 문구가 아니라 실제 자리(class)로 본다
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("plan-estimate"))));
    }

    @Test
    @DisplayName("작성 폼에도 예상 소요 시간을 직접 적는 칸이 있다 - 칩에 없는 길이를 적으러 오는 자리다")
    void newForm_hasEstimateField() throws Exception {
        String html = mockMvc.perform(get("/plans/new").with(memberAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"estimatedMinutes\"");
        assertThat(html).contains("max=\"" + Plan.MAX_ESTIMATED_MINUTES + "\"");
    }

    // ── 주간 뷰에서 한 주 짜기 ────────────────────────────────
    //
    // 예전에는 칸마다 '+ 추가' 링크였고 누르면 작성 폼으로 나갔다 - 일곱 칸을 채우려면
    // 일곱 번 나갔다 돌아와야 했으니 주간 뷰는 사실상 조회 화면이었다.

    @Test
    @DisplayName("주간 뷰의 칸마다 그날의 한 줄 입력이 있다")
    void weekly_hasPerDayQuickAdd() throws Exception {
        given(planService.findWeek(any(LocalDate.class), eq(MEMBER_ID))).willReturn(List.of());

        String html = mockMvc.perform(get("/plans/weekly").param("date", "2026-09-17")
                        .with(memberAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("week-add-input");
        // 일곱 칸 모두 - 하나라도 빠지면 그 요일만 예전 방식으로 돌아간다
        for (int day = 14; day <= 20; day++) {
            assertThat(html).as("9월 %d일 칸에 입력이 없다", day)
                    .contains("data-date=\"2026-09-" + day + "\"");
        }
    }

    @Test
    @DisplayName("POST /plans?view=weekly - JS 없이 제출해도 주간 뷰로 돌아온다")
    void create_returnsToWeeklyView() throws Exception {
        mockMvc.perform(post("/plans")
                        .param("title", "월요일 스터디")
                        .param("category", "MAJOR")
                        .param("planDate", "2026-09-14")
                        .param("repeatType", "NONE")
                        .param("view", "weekly")
                        .with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/plans/weekly?date=2026-09-14"));
    }

    @Test
    @DisplayName("POST /plans - 모르는 view 값은 기본(일간)으로 다룬다 - 주소를 통째로 받으면 열린 리다이렉트가 된다")
    void create_ignoresUnknownView() throws Exception {
        mockMvc.perform(post("/plans")
                        .param("title", "월요일 스터디")
                        .param("category", "MAJOR")
                        .param("planDate", "2026-09-14")
                        .param("repeatType", "NONE")
                        .param("view", "https://example.com")
                        .with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/plans/daily?date=2026-09-14"));
    }

    @Test
    @DisplayName("GET /plans/{id}/week-cell - 주간 격자 한 칸 조각을 돌려준다")
    void weekCell() throws Exception {
        Plan plan = plan();
        ReflectionTestUtils.setField(plan, "id", 5L);
        given(planService.findOwned(5L, MEMBER_ID)).willReturn(plan);

        mockMvc.perform(get("/plans/5/week-cell").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("week-plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("자료구조 공부")));
    }

    @Test
    @DisplayName("POST /plans/copy-week - 지난주를 이번 주로 가져오고 건수를 알려 준다")
    void copyWeek() throws Exception {
        given(planService.copyWeek(MEMBER_ID, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14)))
                .willReturn(3);

        mockMvc.perform(post("/plans/copy-week")
                        .param("week", "2026-09-17")   // 주 가운데 날짜를 넣어도 그 주로 다룬다
                        .with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/plans/weekly?date=2026-09-14"))
                .andExpect(flash().attribute("message",
                        org.hamcrest.Matchers.containsString("3개")));
    }

    @Test
    @DisplayName("POST /plans/copy-week - 가져올 것이 없으면 그렇게 말한다 - 0건은 실패가 아니다")
    void copyWeek_nothingToCopy() throws Exception {
        given(planService.copyWeek(eq(MEMBER_ID), any(LocalDate.class), any(LocalDate.class)))
                .willReturn(0);

        mockMvc.perform(post("/plans/copy-week")
                        .param("week", "2026-09-14")
                        .with(csrf()).with(memberAuth()))
                .andExpect(flash().attribute("message", "가져올 지난주 계획이 없습니다."));
    }

    @Test
    @DisplayName("POST /plans - 하루를 넘는 예상 소요 시간이면 폼으로 돌아가고 저장하지 않는다")
    void create_rejectsAbsurdEstimate() throws Exception {
        mockMvc.perform(post("/plans")
                        .param("title", "자료구조 공부")
                        .param("category", "CODING_TEST")
                        .param("planDate", "2026-07-09")
                        .param("repeatType", "NONE")
                        .param("estimatedMinutes", String.valueOf(Plan.MAX_ESTIMATED_MINUTES + 1))
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"))
                .andExpect(model().attributeHasFieldErrors("planForm", "estimatedMinutes"));

        then(planService).should(never()).create(any(), any());
    }

}
