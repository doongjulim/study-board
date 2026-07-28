package com.example.board.plan.controller;

import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.service.PlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class PlanControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PlanService planService;

    private Plan plan() {
        return new Plan("자료구조 공부", "스택, 큐 복습", "동주",
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    // ── 조회 화면 ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /plans - 일간 뷰로 리다이렉트한다")
    void home() throws Exception {
        mockMvc.perform(get("/plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily"));
    }

    @Test
    @DisplayName("GET /plans/daily - 일간 뷰가 200 을 반환한다")
    void daily() throws Exception {
        given(planService.findDaily(any(LocalDate.class))).willReturn(List.of());

        mockMvc.perform(get("/plans/daily"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/daily"))
                .andExpect(model().attributeExists("date", "plans", "prevDate", "nextDate"));
    }

    @Test
    @DisplayName("GET /plans/daily?date=2026-07-09 - 지정한 날짜가 모델에 담긴다")
    void daily_withDate() throws Exception {
        given(planService.findDaily(LocalDate.of(2026, 7, 9))).willReturn(List.of());

        mockMvc.perform(get("/plans/daily").param("date", "2026-07-09"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("date", LocalDate.of(2026, 7, 9)));
    }

    @Test
    @DisplayName("GET /plans/weekly - 주간 뷰는 항상 7일을 보여준다")
    void weekly() throws Exception {
        given(planService.findWeek(any(LocalDate.class))).willReturn(List.of());

        mockMvc.perform(get("/plans/weekly").param("date", "2026-07-08"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/weekly"))
                .andExpect(model().attribute("weekDays", hasSize(7)))
                .andExpect(model().attribute("weekStart", LocalDate.of(2026, 7, 6)));
    }

    @Test
    @DisplayName("GET /plans/monthly - 월간 캘린더가 200 을 반환한다")
    void monthly() throws Exception {
        given(planService.findMonth(any())).willReturn(List.of());

        mockMvc.perform(get("/plans/monthly").param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/monthly"))
                .andExpect(model().attributeExists("weeks", "plansByDate"));
    }

    @Test
    @DisplayName("GET /plans/shared - 공유 플랜 목록이 200 을 반환한다")
    void shared() throws Exception {
        given(planService.findShared(any())).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/plans/shared"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/shared"));
    }

    // ── 등록 / 수정 / 삭제 ────────────────────────────────────

    @Test
    @DisplayName("GET /plans/new - 작성 폼이 200 을 반환한다")
    void createForm() throws Exception {
        mockMvc.perform(get("/plans/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"))
                .andExpect(model().attributeExists("planForm"));
    }

    @Test
    @DisplayName("POST /plans - 유효한 입력이면 저장 후 해당 날짜 일간 뷰로 이동한다")
    void create() throws Exception {
        given(planService.create(any())).willReturn(1L);

        mockMvc.perform(post("/plans").with(csrf())
                        .param("title", "자료구조 공부")
                        .param("writer", "동주")
                        .param("planDate", "2026-07-09")
                        .param("startTime", "10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().create(any());
    }

    @Test
    @DisplayName("POST /plans - 제목이 비면 폼으로 돌아가고 저장하지 않는다")
    void create_invalid() throws Exception {
        mockMvc.perform(post("/plans").with(csrf())
                        .param("title", "")
                        .param("writer", "동주")
                        .param("planDate", "2026-07-09"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"));

        then(planService).should(never()).create(any());
    }

    @Test
    @DisplayName("POST /plans - 종료 시간이 시작 시간보다 빠르면 폼으로 돌아간다")
    void create_invalidTimeRange() throws Exception {
        mockMvc.perform(post("/plans").with(csrf())
                        .param("title", "자료구조 공부")
                        .param("writer", "동주")
                        .param("planDate", "2026-07-09")
                        .param("startTime", "12:00")
                        .param("endTime", "10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans/form"));

        then(planService).should(never()).create(any());
    }

    @Test
    @DisplayName("POST /plans/{id}/delete - 삭제 후 해당 날짜 일간 뷰로 이동한다")
    void deletePlan() throws Exception {
        given(planService.findById(1L)).willReturn(plan());

        mockMvc.perform(post("/plans/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));

        then(planService).should().delete(1L);
    }

    // ── 상태 변경 ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /plans/{id}/toggle - 완료 전환 후 해당 날짜로 이동한다")
    void toggle() throws Exception {
        given(planService.toggleCompleted(1L)).willReturn(plan());

        mockMvc.perform(post("/plans/1/toggle").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));
    }

    @Test
    @DisplayName("POST /plans/{id}/share - 공유 전환 후 해당 날짜로 이동한다")
    void share() throws Exception {
        given(planService.toggleShared(1L)).willReturn(plan());

        mockMvc.perform(post("/plans/1/share").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/daily?date=2026-07-09"));
    }
}
