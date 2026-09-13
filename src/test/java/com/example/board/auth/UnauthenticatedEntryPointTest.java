package com.example.board.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * 로그인하지 않은 요청을 어떻게 돌려보내는가.
 *
 * <p>전부 로그인 화면으로 302 를 보내던 때, 화면 안의 스크립트가 부르는 요청은
 * 리다이렉트를 따라가 <b>200 + 로그인 HTML</b> 을 받았다. {@code res.ok} 가 참이라
 * "실패하면 이렇게" 방어를 그냥 통과했고, EventSource 는 영원히 재접속했다.</p>
 */
class UnauthenticatedEntryPointTest {

    private final UnauthenticatedEntryPoint entryPoint = new UnauthenticatedEntryPoint();

    private MockHttpServletResponse commence(String uri, String query, String acceptHeader,
                                             String requestedWith) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setQueryString(query);
        if (acceptHeader != null) {
            request.addHeader("Accept", acceptHeader);
        }
        if (requestedWith != null) {
            request.addHeader("X-Requested-With", requestedWith);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new InsufficientAuthenticationException("no auth"));
        return response;
    }

    // ── 화면 이동 ─────────────────────────────────────────────

    @Test
    @DisplayName("브라우저의 화면 이동은 로그인 화면으로 보낸다 - 원래 가려던 경로를 들고")
    void navigationRedirects() throws IOException {
        MockHttpServletResponse response = commence("/plans/daily", "date=2026-09-13",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", null);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/login?redirect=%2Fplans%2Fdaily%3Fdate%3D2026-09-13");
    }

    @Test
    @DisplayName("Accept 가 없으면 화면으로 본다 - 주소창에 직접 친 경우가 그렇다")
    void missingAcceptRedirects() throws IOException {
        assertThat(commence("/plans/daily", null, null, null).getStatus()).isEqualTo(302);
    }

    @Test
    @DisplayName("아무거나 받겠다(*/*)도 화면으로 본다")
    void wildcardAcceptRedirects() throws IOException {
        assertThat(commence("/plans/daily", null, "*/*", null).getStatus()).isEqualTo(302);
    }

    // ── 데이터를 기다리는 요청 ────────────────────────────────

    @Test
    @DisplayName("JSON 을 원하면 401 - 부르는 쪽의 오류 처리가 실제로 동작해야 한다")
    void jsonGets401() throws IOException {
        MockHttpServletResponse response = commence("/notifications", null, "application/json", null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("SSE 구독은 401 - 리다이렉트를 주면 EventSource 가 영원히 재접속한다")
    void eventStreamGets401() throws IOException {
        assertThat(commence("/notifications/subscribe", null, "text/event-stream", null).getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("X-Requested-With 가 붙어도 401")
    void xhrGets401() throws IOException {
        assertThat(commence("/notifications", null, null, "XMLHttpRequest").getStatus())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("401 응답에는 한글이 깨지지 않는 안내가 실린다")
    void unauthorizedCarriesMessage() throws IOException {
        MockHttpServletResponse response = commence("/notifications", null, "application/json", null);

        assertThat(response.getContentAsString()).isEqualTo("로그인이 필요합니다.");
    }
}
