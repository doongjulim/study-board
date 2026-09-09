package com.example.board.auth.oauth;

import com.example.board.auth.CookiePolicy;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 인가 요청(state 등)을 <b>세션이 아니라 쿠키</b>에 둔다.
 *
 * <p>기본 구현은 HttpSession 을 쓴다. 그런데 이 애플리케이션은 무상태다 -
 * 세션 관리를 켜 두면 요청마다 CSRF 토큰이 갈아 끼워져 화면의 토큰이 클릭 전에 죽는다
 * (CLAUDE.md 의 '세션 관리' 함정). 소셜 로그인 하나 때문에 그 구조를 되돌릴 수는 없다.</p>
 *
 * <p>이 클래스가 정하는 것은 <b>어디에 두는가</b>뿐이다.
 * 무엇을 담고 어떻게 지키는가는 {@link AuthorizationRequestCodec} 가,
 * 쿠키 속성은 {@link CookiePolicy} 가 정한다 - 셋이 한 클래스에 있으면
 * 하나를 고칠 때마다 나머지 둘을 다시 읽어야 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    /** 제공자 화면에서 로그인하고 동의하는 데 걸리는 시간. 끝나면 지우므로 더 길 이유가 없다 */
    private static final int MAX_AGE_SECONDS = 180;

    private final AuthorizationRequestCodec codec;
    private final CookiePolicy cookiePolicy;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return read(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            clear(response);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookiePolicy.build(COOKIE_NAME, codec.encode(authorizationRequest), MAX_AGE_SECONDS));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest found = read(request);
        clear(response);
        return found;
    }

    private OAuth2AuthorizationRequest read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .map(codec::decode)
                .orElse(null);
    }

    private void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookiePolicy.expire(COOKIE_NAME));
    }
}
