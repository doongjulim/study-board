package com.example.board.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 로그인하지 않은 요청을 어떻게 돌려보낼지 정한다.
 *
 * <p>― <b>왜 두 갈래인가</b><br>
 * 예전에는 전부 {@code /login} 으로 302 를 보냈다. 화면 이동에는 맞지만, 화면 안의 스크립트가
 * 부르는 요청에는 맞지 않는다. 브라우저는 리다이렉트를 <b>따라가</b> 로그인 HTML 을 받아 오고,
 * 그 응답의 상태는 <b>200</b> 이다. 그래서:</p>
 * <ul>
 *   <li>{@code fetch('/notifications')} 는 {@code res.ok} 가 참이라 "실패하면 이렇게" 라고
 *       적어 둔 방어를 그냥 통과하고, {@code res.json()} 이 HTML 을 만나 예외를 던진다 -
 *       벨이 아무 말 없이 죽는다</li>
 *   <li>{@code EventSource} 는 형식이 다르니 오류로 보고 <b>스스로 재접속한다</b>.
 *       로그아웃한 뒤에도, 리프레시 토큰이 만료된 뒤에도, 탭이 열려 있는 한 계속</li>
 * </ul>
 *
 * <p>그래서 <b>요청이 무엇을 원하는지</b>로 나눈다. 데이터를 원하면 401 을 준다 -
 * 부르는 쪽의 오류 처리가 실제로 동작하고, EventSource 도 끊을 근거가 생긴다.
 * 주소창이 움직이는 요청(화면 이동)에만 로그인 화면으로 보낸다.</p>
 *
 * <p>빈으로 등록하지 않고 {@code SecurityConfig} 가 직접 만든다. 의존이 없어 빈일 이유가 없고,
 * {@code @Component} 로 두면 이 설정을 import 하는 컨트롤러 테스트 슬라이스가 전부 컨텍스트를
 * 만들지 못한다(CLAUDE.md 의 '@WebMvcTest 와 새 빈' 함정).</p>
 *
 * <p>판단 근거로 {@code Accept} 를 쓰는 이유: 경로 규칙(예: {@code /api/**})으로 가르면
 * 서버 렌더링과 JSON 을 같은 prefix 아래 섞어 둔 이 구조에서는 곧 어긋난다.
 * 브라우저의 화면 이동은 {@code text/html} 을 먼저 요구하고, fetch 와 EventSource 는 그렇지 않다 -
 * 요청 스스로가 이미 답을 갖고 있다.</p>
 */
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (wantsData(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("로그인이 필요합니다.");
            return;
        }
        // 원래 가려던 경로를 들고 로그인 화면으로 - 로그인하면 그 자리로 돌아간다
        response.sendRedirect("/login?redirect=" + URLEncoder.encode(target(request), StandardCharsets.UTF_8));
    }

    /**
     * 이 요청은 화면이 아니라 데이터를 기다리고 있는가.
     *
     * <p>브라우저의 화면 이동은 {@code Accept} 에 {@code text/html} 을 먼저 적어 보낸다.
     * {@code fetch}/{@code EventSource} 는 그렇지 않다 - 기본값이거나
     * {@code application/json}·{@code text/event-stream} 을 명시한다.</p>
     */
    private boolean wantsData(HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null) {
            return false; // 알 수 없으면 화면으로 본다 - 사람이 주소창에 친 경우가 그렇다
        }
        if (accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)
                || accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        return !accept.contains(MediaType.TEXT_HTML_VALUE) && !accept.contains("*/*");
    }

    private String target(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query != null ? "?" + query : "");
    }
}
