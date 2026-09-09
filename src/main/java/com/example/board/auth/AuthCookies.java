package com.example.board.auth;

import com.example.board.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 인증 쿠키(액세스/리프레시) 읽기·쓰기 담당.
 *
 * <p>쿠키 속성은 {@link CookiePolicy} 가 정한다 - 이 클래스는 <b>어떤 토큰을 얼마나 오래</b>
 * 실을지만 정한다. 속성을 여기서도 정하면 쿠키를 내려보내는 곳마다 정책이 갈린다
 * (실제로 소셜 로그인의 인가 요청 쿠키만 SameSite 가 빠져 있었다).</p>
 */
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private final CookiePolicy cookiePolicy;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public AuthCookies(CookiePolicy cookiePolicy,
                       @Value("${jwt.access-token-minutes}") long accessTokenMinutes,
                       @Value("${jwt.refresh-token-days}") long refreshTokenDays) {
        this.cookiePolicy = cookiePolicy;
        this.accessTokenValidity = Duration.ofMinutes(accessTokenMinutes);
        this.refreshTokenValidity = Duration.ofDays(refreshTokenDays);
    }

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return read(request, ACCESS_TOKEN);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return read(request, REFRESH_TOKEN);
    }

    /** 발급된 토큰 쌍을 쿠키로 내려보낸다 (로그인·자동 갱신 공용) */
    public void write(HttpServletResponse response, TokenService.TokenPair tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(ACCESS_TOKEN, tokens.accessToken(), accessTokenValidity.toSeconds()));
        response.addHeader(HttpHeaders.SET_COOKIE,
                build(REFRESH_TOKEN, tokens.refreshToken(), refreshTokenValidity.toSeconds()));
    }

    /** 두 쿠키를 즉시 만료시킨다 (로그아웃·무효 토큰 정리) */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_TOKEN, "", 0));
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_TOKEN, "", 0));
    }

    private String build(String name, String value, long maxAgeSeconds) {
        return cookiePolicy.build(name, value, maxAgeSeconds);
    }

    private Optional<String> read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
