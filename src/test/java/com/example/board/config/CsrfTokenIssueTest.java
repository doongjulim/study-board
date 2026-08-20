package com.example.board.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 토큰이 <b>제때</b> 발급되는지 본다.
 *
 * <p>다른 테스트들은 {@code with(csrf())} 로 토큰을 손수 만들어 넣는다. 그래서 "서버가 토큰을
 * 내려 주기는 하는가" 는 어디서도 확인되지 않았고, 실제로 온보딩 '건너뛰고 둘러보기' 가 브라우저에서
 * 403 으로 막혔다 - 폼에는 토큰이 박혔는데 쿠키에는 아무것도 없었다.</p>
 *
 * <p>브라우저(E2E)로도 잡히지만 그 쪽은 느리고 무겁다. 원인이 보안 설정 한 줄이므로
 * 그 한 줄을 지키는 테스트를 여기에 둔다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CSRF 토큰 발급")
class CsrfTokenIssueTest {

    private static final Pattern HIDDEN_CSRF =
            Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("화면을 한 번 열면 XSRF-TOKEN 쿠키가 함께 내려온다 - 이게 없으면 다음 POST 가 403 이 된다")
    void issuesCookieOnPageLoad() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("화면에서 받은 토큰과 쿠키로 그다음 POST 가 통과한다 - 둘이 어긋나면 사용자는 403 을 본다")
    void acceptsTokenTakenFromRenderedForm() throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();

        Cookie csrfCookie = page.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).as("화면 응답에 XSRF-TOKEN 쿠키가 있어야 한다").isNotNull();

        Matcher hidden = HIDDEN_CSRF.matcher(page.getResponse().getContentAsString());
        assertThat(hidden.find()).as("폼에 _csrf 숨은 필드가 박혀 있어야 한다").isTrue();

        mockMvc.perform(post("/logout")
                        .cookie(csrfCookie)
                        .param("_csrf", hidden.group(1)))
                .andExpect(status().is3xxRedirection());
    }
}
