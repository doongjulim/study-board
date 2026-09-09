package com.example.board.notification.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;
    @MockBean TokenService tokenService;

    /** 알림은 로그인한 회원만 사용할 수 있다 */
    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(1L, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @Test
    @DisplayName("GET /notifications/subscribe - SSE 스트림으로 응답한다")
    void subscribe() throws Exception {
        given(notificationService.subscribe(1L, null)).willReturn(new SseEmitter());

        mockMvc.perform(get("/notifications/subscribe").with(memberAuth()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /notifications - 읽지 않은 개수와 최근 알림을 JSON 으로 반환한다")
    void list() throws Exception {
        given(notificationService.countUnread(1L)).willReturn(3L);
        given(notificationService.findRecent(1L)).willReturn(List.of());

        mockMvc.perform(get("/notifications").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications").isArray());
    }

    @Test
    @DisplayName("POST /notifications/read-all - 모두 읽음 처리한다")
    void readAll() throws Exception {
        mockMvc.perform(post("/notifications/read-all").with(csrf()).with(memberAuth()))
                .andExpect(status().isOk());

        then(notificationService).should().markAllAsRead(1L);
    }

    // ── 알림 하나 단위 조작 ────────────────────────────────────
    // 벨 패널이 fetch 로 부르는 자리다. 패널을 열었다는 사실이 읽음을 정하지 않는다.

    @Test
    @DisplayName("POST /notifications/{id}/read - 그 알림만 읽음 처리한다")
    void readOne() throws Exception {
        mockMvc.perform(post("/notifications/7/read").with(memberAuth()).with(csrf()))
                .andExpect(status().isOk());

        then(notificationService).should().markAsRead(1L, 7L);
    }

    @Test
    @DisplayName("POST /notifications/{id}/delete - 그 알림만 지운다")
    void deleteOne() throws Exception {
        mockMvc.perform(post("/notifications/7/delete").with(memberAuth()).with(csrf()))
                .andExpect(status().isOk());

        then(notificationService).should().delete(1L, 7L);
    }

    @Test
    @DisplayName("비로그인은 알림을 건드릴 수 없다")
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/notifications/7/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());

        then(notificationService).shouldHaveNoInteractions();
    }

}
