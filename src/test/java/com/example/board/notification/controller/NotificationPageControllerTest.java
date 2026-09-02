package com.example.board.notification.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 벨 패널의 10건 뒤로 밀려난 알림을 보는 화면.
 * 여기 있는 조작은 폼으로 이루어지므로 JS 없이도 동작해야 하고, 조작 뒤에는 보던 페이지로 돌아와야 한다.
 */
@WebMvcTest(NotificationPageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class NotificationPageControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;
    @MockBean TokenService tokenService;

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @Test
    @DisplayName("GET /notifications/all - 내 알림을 페이지로 보여 준다")
    void list() throws Exception {
        given(notificationService.findPage(eq(MEMBER_ID), any()))
                .willReturn(new PageImpl<>(List.of()));
        given(notificationService.countUnread(MEMBER_ID)).willReturn(2L);

        mockMvc.perform(get("/notifications/all").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("notification/list"))
                .andExpect(model().attributeExists("notifications", "pageBlock", "unreadCount"));
    }

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void list_requiresLogin() throws Exception {
        mockMvc.perform(get("/notifications/all"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST 읽음 - 처리 후 보던 페이지로 돌아온다")
    void read_returnsToSamePage() throws Exception {
        // 3페이지에서 하나 읽었는데 1페이지로 튀면 다시 찾아 들어가야 한다
        mockMvc.perform(post("/notifications/all/5/read").param("page", "3")
                        .with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=3"));

        then(notificationService).should().markAsRead(MEMBER_ID, 5L);
    }

    @Test
    @DisplayName("POST 삭제 - 처리 후 보던 페이지로 돌아온다")
    void delete_returnsToSamePage() throws Exception {
        mockMvc.perform(post("/notifications/all/5/delete").param("page", "2")
                        .with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=2"));

        then(notificationService).should().delete(MEMBER_ID, 5L);
    }

    @Test
    @DisplayName("page 를 주지 않으면 첫 페이지로 돌아온다")
    void defaultsToFirstPage() throws Exception {
        mockMvc.perform(post("/notifications/all/5/read").with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=0"));
    }

    @Test
    @DisplayName("음수 page 가 들어와도 첫 페이지로 보정한다")
    void negativePageIsClamped() throws Exception {
        mockMvc.perform(post("/notifications/all/5/read").param("page", "-3")
                        .with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=0"));
    }

    @Test
    @DisplayName("POST 모두 읽음")
    void readAll() throws Exception {
        mockMvc.perform(post("/notifications/all/read-all").param("page", "1")
                        .with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=1"));

        then(notificationService).should().markAllAsRead(MEMBER_ID);
    }

    @Test
    @DisplayName("POST 모두 삭제 - 돌아갈 페이지가 없으므로 첫 페이지로 보낸다")
    void deleteAll() throws Exception {
        mockMvc.perform(post("/notifications/all/delete-all").with(memberAuth()).with(csrf()))
                .andExpect(redirectedUrl("/notifications/all?page=0"));

        then(notificationService).should().deleteAll(MEMBER_ID);
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 거부한다")
    void rejectsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/notifications/all/5/delete").with(memberAuth()))
                .andExpect(status().isForbidden());
    }
}
