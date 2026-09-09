package com.example.board.auth.oauth;

import com.example.board.auth.CookiePolicy;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 인가 요청이 쿠키로 나갔다가 다음 요청에 실려 돌아오는 왕복.
 *
 * <p>세션을 쓰지 않기로 한 이상 이 왕복이 소셜 로그인의 전부다 - 여기가 끊기면
 * 콜백에서 "그런 인가 요청이 없다" 가 되어 로그인이 조용히 실패한다.</p>
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final AuthorizationRequestCodec codec =
            new AuthorizationRequestCodec("test-only-secret-key-must-be-32-bytes-min!");
    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(codec, new CookiePolicy(false));

    private OAuth2AuthorizationRequest request() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("profile", "email"))
                .state("state-value")
                .attributes(Map.of("registration_id", "google"))
                .build();
    }

    /** 브라우저가 하는 일 - 응답의 Set-Cookie 를 다음 요청에 실어 준다 */
    private MockHttpServletRequest requestCarrying(MockHttpServletResponse response) {
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(new Cookie(pair.substring(0, pair.indexOf('=')),
                pair.substring(pair.indexOf('=') + 1)));
        return next;
    }

    @Test
    @DisplayName("저장한 인가 요청을 다음 요청에서 읽어 온다")
    void saveThenLoad() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request(), new MockHttpServletRequest(), response);

        OAuth2AuthorizationRequest loaded =
                repository.loadAuthorizationRequest(requestCarrying(response));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-value");
        assertThat(loaded.getAttributes()).containsEntry("registration_id", "google");
    }

    @Test
    @DisplayName("쿠키에는 HttpOnly 와 SameSite 가 붙는다 - 인증 쿠키와 같은 정책이다")
    void cookieCarriesProjectPolicy() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(request(), new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
        // 제공자 화면에 다녀올 동안만 살면 된다
        assertThat(setCookie).contains("Max-Age=180");
    }

    @Test
    @DisplayName("꺼내 가면 쿠키를 즉시 만료시킨다 - 다 쓴 state 를 남겨 둘 이유가 없다")
    void removeExpiresCookie() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request(), new MockHttpServletRequest(), saved);
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(requestCarrying(saved), response);

        assertThat(removed).isNotNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("쿠키가 없으면 null - 예외가 아니다")
    void loadWithoutCookie() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("남이 만든 쿠키는 없는 것으로 본다")
    void forgedCookieIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                "aGVsbG8.aGVsbG8"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    @DisplayName("null 을 저장하라는 것은 지우라는 뜻이다")
    void saveNullClears() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }
}
