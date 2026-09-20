package com.example.board.application.controller;

import com.example.board.application.domain.ApplicationResult;
import com.example.board.application.domain.ApplicationStage;
import com.example.board.application.domain.JobApplication;
import com.example.board.application.domain.StageFocus;
import com.example.board.application.dto.JobApplicationForm;
import com.example.board.application.service.JobApplicationService;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.support.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobApplicationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        AuthCookies.class, CookiePolicy.class, FixedClockConfig.class})
class JobApplicationControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean JobApplicationService applicationService;
    @MockBean TokenService tokenService;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(applicationService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private JobApplication application() {
        Member owner = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(owner, "id", MEMBER_ID);
        JobApplication application = new JobApplication(owner, "카카오", "백엔드",
                ApplicationStage.CODING_TEST, FixedClockConfig.TODAY.plusDays(3), "1차 코테");
        ReflectionTestUtils.setField(application, "id", 5L);
        return application;
    }

    @Test
    @DisplayName("GET /applications - 목록·등록 폼과 '지금 단계' 요약을 함께 보여 준다")
    void list() throws Exception {
        given(applicationService.findMine(MEMBER_ID)).willReturn(List.of(application()));
        given(applicationService.currentFocus(eq(MEMBER_ID), any(LocalDate.class)))
                .willReturn(List.of(new StageFocus(PlanCategory.CODING_TEST, 1, 0)));

        String html = mockMvc.perform(get("/applications").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/list"))
                .andExpect(model().attributeExists("applications", "focus", "applicationForm", "stages"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("카카오").contains("코딩테스트");
        // 마감은 D-Day 와 같은 모양으로 나란히 놓인다
        assertThat(html).contains("D-3");
    }

    @Test
    @DisplayName("GET /applications - 비로그인은 로그인 화면으로 보낸다 - 어디에 지원했는지는 가장 사적인 기록이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/applications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    @Test
    @DisplayName("POST /applications - 회사 이름만 있어도 등록된다 - 공고를 보다가 바로 적는 자리다")
    void createWithCompanyOnly() throws Exception {
        mockMvc.perform(post("/applications")
                        .param("company", "카카오")
                        .param("stage", "DOCUMENT")
                        .with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/applications"));

        then(applicationService).should().create(any(JobApplicationForm.class), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("POST /applications - 회사가 비면 저장하지 않고 목록을 다시 보여 준다")
    void rejectsBlankCompany() throws Exception {
        given(applicationService.findMine(MEMBER_ID)).willReturn(List.of());
        given(applicationService.currentFocus(eq(MEMBER_ID), any(LocalDate.class))).willReturn(List.of());

        mockMvc.perform(post("/applications")
                        .param("company", "")
                        .param("stage", "DOCUMENT")
                        .with(csrf()).with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/list"))
                .andExpect(model().attributeHasFieldErrors("applicationForm", "company"));

        then(applicationService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("GET /applications/{id}/edit - 수정 폼에 현재 값이 채워진다")
    void editForm() throws Exception {
        given(applicationService.findOwned(5L, MEMBER_ID)).willReturn(application());

        mockMvc.perform(get("/applications/5/edit").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("applications/form"))
                .andExpect(model().attribute("applicationId", 5L))
                .andExpect(model().attributeExists("applicationForm", "results"));
    }

    @Test
    @DisplayName("POST /applications/{id}/edit - 단계와 결과를 함께 바꾼다")
    void edit() throws Exception {
        mockMvc.perform(post("/applications/5/edit")
                        .param("company", "카카오")
                        .param("stage", "INTERVIEW_FIRST")
                        .param("result", "IN_PROGRESS")
                        .with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/applications"));

        then(applicationService).should().update(eq(5L), any(JobApplicationForm.class), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("남의 지원 기록은 서비스가 막는다 - 화면에서 감추는 것은 안내이지 방어가 아니다")
    void editRejectsOthers() throws Exception {
        given(applicationService.findOwned(5L, MEMBER_ID))
                .willThrow(new AccessDeniedException("본인 것이 아님"));

        mockMvc.perform(get("/applications/5/edit").with(memberAuth()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /applications/{id}/delete - 삭제 후 목록으로 돌아온다")
    void delete() throws Exception {
        mockMvc.perform(post("/applications/5/delete").with(csrf()).with(memberAuth()))
                .andExpect(redirectedUrl("/applications"));

        then(applicationService).should().delete(5L, MEMBER_ID);
    }

    @Test
    @DisplayName("CSRF 토큰 없이는 통하지 않는다")
    void rejectsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/applications").param("company", "카카오").with(memberAuth()))
                .andExpect(status().isForbidden());

        then(applicationService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("결과 선택지에 '포기' 가 있다 - 스스로 그만둔 것을 불합격으로 적게 하지 않는다")
    void resultsIncludeWithdrawn() throws Exception {
        given(applicationService.findOwned(5L, MEMBER_ID)).willReturn(application());

        String html = mockMvc.perform(get("/applications/5/edit").with(memberAuth()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(ApplicationResult.WITHDRAWN.getLabel());
    }
}
