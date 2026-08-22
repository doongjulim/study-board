package com.example.board.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화면에 박아 준 CSRF 토큰이 그다음 요청에서 <b>실제로 통하는지</b> 본다.
 *
 * <p>다른 컨트롤러 테스트는 전부 {@code with(csrf())} 로 토큰을 손수 만들어 넣는다. 편하지만,
 * 그래서 "서버가 내려 준 토큰과 서버가 검사하는 토큰이 같은가" 는 어디서도 확인되지 않았다.
 * 실제로 이 틈으로 온보딩 '건너뛰고 둘러보기' 가 브라우저에서 403 으로 막혔다 -
 * 폼에는 토큰이 박혀 있었는데 서버에 남은 것은 다른 토큰이었다.</p>
 *
 * <p>여기서는 <b>손으로 만들지 않는다.</b> 화면을 열어 거기 박힌 값을 그대로 꺼내 쓴다.
 * 토큰을 어디에 보관하든(세션이든 쿠키든) 이 왕복이 되면 사용자에게도 된다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CSRF 토큰 왕복")
class CsrfTokenIssueTest {

    private static final Pattern HIDDEN_CSRF =
            Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Test
    @DisplayName("세션 관리 필터가 체인에 없다 - 있으면 요청마다 CSRF 토큰이 갈아 끼워진다")
    void hasNoSessionManagementFilter() {
        List<Filter> filters = springSecurityFilterChain.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .toList();

        assertThat(filters)
                .as("""
                        SessionManagementFilter 는 '저장소에서 SecurityContext 를 못 읽었는데 인증은 있다' 를
                        방금 로그인한 것으로 본다. JWT 는 요청마다 인증을 새로 채우므로 이 조건이 늘 참이 되고,
                        CsrfAuthenticationStrategy 가 매 요청 토큰을 바꿔 화면의 토큰이 즉시 죽는다.""")
                .noneMatch(SessionManagementFilter.class::isInstance);
    }

    @Test
    @DisplayName("서버가 그린 폼에는 CSRF 토큰이 들어 있다 - 없으면 어떤 폼도 제출되지 않는다")
    void rendersCsrfTokenInForm() throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();

        assertThat(HIDDEN_CSRF.matcher(page.getResponse().getContentAsString()).find())
                .as("폼에 _csrf 숨은 필드가 박혀 있어야 한다").isTrue();
    }

    @Test
    @DisplayName("그 화면에서 꺼낸 토큰으로 바로 다음 POST 가 통과한다 - 온보딩 건너뛰기가 403 이던 회귀")
    void acceptsTokenTakenFromRenderedForm() throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();

        // 브라우저가 하는 일과 같게 - 화면을 받은 그 연결을 이어서 쓴다
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        Matcher hidden = HIDDEN_CSRF.matcher(page.getResponse().getContentAsString());
        assertThat(hidden.find()).as("폼에 _csrf 숨은 필드가 박혀 있어야 한다").isTrue();

        mockMvc.perform(post("/logout")
                        .session(session != null ? session : new MockHttpSession())
                        .param("_csrf", hidden.group(1)))
                .andExpect(status().is3xxRedirection());
    }
}
