package com.example.board.dday.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.dday.domain.Dday;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DdayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class DdayControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean DdayService ddayService;
    @MockBean TokenService tokenService;

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private Dday dday() {
        Member owner = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(owner, "id", MEMBER_ID);
        return new Dday(owner, "정보처리기사 실기", LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("GET /ddays - 목록과 등록 폼이 200 을 반환한다")
    void list() throws Exception {
        given(ddayService.findMine(MEMBER_ID)).willReturn(List.of(dday()));

        mockMvc.perform(get("/ddays").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("ddays/list"))
                .andExpect(model().attributeExists("ddays", "ddayForm", "today"));
    }

    @Test
    @DisplayName("POST /ddays - 유효한 입력이면 등록 후 목록으로 이동한다")
    void create() throws Exception {
        mockMvc.perform(post("/ddays").with(csrf()).with(memberAuth())
                        .param("title", "정보처리기사 실기")
                        .param("targetDate", "2026-09-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ddays"));

        then(ddayService).should().create(any(DdayForm.class), eq(MEMBER_ID));
    }

    @Test
    @DisplayName("POST /ddays - 제목이 비면 목록 화면으로 돌아가고 저장하지 않는다")
    void create_invalid() throws Exception {
        given(ddayService.findMine(MEMBER_ID)).willReturn(List.of());

        mockMvc.perform(post("/ddays").with(csrf()).with(memberAuth())
                        .param("title", "")
                        .param("targetDate", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("ddays/list"))
                .andExpect(model().attributeHasFieldErrors("ddayForm", "title"));

        then(ddayService).should(never()).create(any(), any());
    }

    @Test
    @DisplayName("POST /ddays/{id}/delete - 삭제 후 목록으로 이동한다")
    void delete() throws Exception {
        mockMvc.perform(post("/ddays/1/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ddays"));

        then(ddayService).should().delete(1L, MEMBER_ID);
    }

    @Test
    @DisplayName("비로그인으로 D-Day 에 접근하면 로그인 페이지로 리다이렉트한다")
    void list_requiresLogin() throws Exception {
        mockMvc.perform(get("/ddays"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }
}
