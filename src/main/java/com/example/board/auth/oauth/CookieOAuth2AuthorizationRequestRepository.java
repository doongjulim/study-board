package com.example.board.auth.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Arrays;
import java.util.Base64;

/**
 * 인가 요청(state 등)을 <b>세션이 아니라 쿠키</b>에 둔다.
 *
 * <p>기본 구현은 HttpSession 을 쓴다. 그런데 이 애플리케이션은 무상태다 -
 * 세션 관리를 켜 두면 요청마다 CSRF 토큰이 갈아 끼워져 화면의 토큰이 클릭 전에 죽는다
 * (CLAUDE.md 의 '세션 관리' 함정). 소셜 로그인 하나 때문에 그 구조를 되돌릴 수는 없다.</p>
 *
 * <p>쿠키는 짧게 산다 - 제공자에 다녀오는 몇 분이면 충분하고, 끝나면 지운다.
 * HttpOnly 라 화면 스크립트가 읽지 못한다. 담기는 것은 state 와 redirect 정보이며 비밀은 아니지만,
 * 굳이 읽히게 둘 이유도 없다.</p>
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
    /** 제공자 화면에서 로그인하고 동의하는 데 걸리는 시간 */
    private static final int MAX_AGE_SECONDS = 180;

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
        Cookie cookie = new Cookie(COOKIE_NAME, encode(authorizationRequest));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(MAX_AGE_SECONDS);
        response.addCookie(cookie);
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
                .map(this::decode)
                .orElse(null);
    }

    private String encode(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder()
                .encodeToString(SerializationUtils.serialize(authorizationRequest));
    }

    private OAuth2AuthorizationRequest decode(String value) {
        try {
            Object deserialized = SerializationUtils.deserialize(Base64.getUrlDecoder().decode(value));
            return (deserialized instanceof OAuth2AuthorizationRequest request) ? request : null;
        } catch (IllegalArgumentException | SecurityException e) {
            // 손상되었거나 우리가 만들지 않은 값이다. 없는 것으로 보면 로그인이 처음부터 다시 시작된다
            return null;
        }
    }

    private void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
