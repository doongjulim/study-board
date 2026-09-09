package com.example.board.calendar.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.calendar.service.CalendarFeedService;
import com.example.board.config.SecurityConfig;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class,
        CalendarControllerTest.FixedClockConfig.class})
class CalendarControllerTest {

    private static final long MEMBER_ID = 1L;

    @TestConfiguration
    static class FixedClockConfig {
        static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

        @Bean
        Clock clock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean CalendarFeedService calendarFeedService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(calendarFeedService);
    }

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @Test
    @DisplayName("GET /calendar/{token}.ics - 로그인 없이 열린다 (구글 캘린더가 읽어 가야 한다)")
    void feedIsPublic() throws Exception {
        given(calendarFeedService.renderFeed("abc-token"))
                .willReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/calendar/abc-token.ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")));
    }

    @Test
    @DisplayName("GET /calendar/{token}.ics - 캘린더 앱이 옛 내용을 붙들지 않게 캐시를 막는다")
    void feedIsNotCached() throws Exception {
        given(calendarFeedService.renderFeed(any())).willReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/calendar/abc-token.ics"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
    }

    @Test
    @DisplayName("GET /calendar/export.csv - 로그인해야 내려받을 수 있다")
    void csvRequiresLogin() throws Exception {
        mockMvc.perform(get("/calendar/export.csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));

        then(calendarFeedService).should(never()).renderCsv(any(), any(), any());
    }

    @Test
    @DisplayName("GET /calendar/export.csv - 기간을 비우면 최근 석 달을 받는다")
    void csvDefaultsToLastThreeMonths() throws Exception {
        given(calendarFeedService.renderCsv(any(), any(), any())).willReturn("날짜\r\n");

        mockMvc.perform(get("/calendar/export.csv").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));

        then(calendarFeedService).should().renderCsv(
                MEMBER_ID, FixedClockConfig.TODAY.minusMonths(3), FixedClockConfig.TODAY);
    }

    @Test
    @DisplayName("GET /calendar/export.csv - 고른 기간이 있으면 그대로 쓴다")
    void csvUsesGivenRange() throws Exception {
        given(calendarFeedService.renderCsv(any(), any(), any())).willReturn("날짜\r\n");

        mockMvc.perform(get("/calendar/export.csv").with(memberAuth())
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isOk());

        then(calendarFeedService).should().renderCsv(
                MEMBER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("POST /calendar/token - 발급 후 마이페이지로 돌아간다")
    void issueToken() throws Exception {
        mockMvc.perform(post("/calendar/token").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/me"));

        then(calendarFeedService).should().issueToken(MEMBER_ID);
    }

    @Test
    @DisplayName("POST /calendar/token/revoke - 구독을 끊고 마이페이지로 돌아간다")
    void revokeToken() throws Exception {
        mockMvc.perform(post("/calendar/token/revoke").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/me"));

        then(calendarFeedService).should().revokeToken(MEMBER_ID);
    }

    @Test
    @DisplayName("POST /calendar/token - 로그인 없이는 발급할 수 없다")
    void issueRequiresLogin() throws Exception {
        mockMvc.perform(post("/calendar/token").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));

        then(calendarFeedService).should(never()).issueToken(any());
    }
}
