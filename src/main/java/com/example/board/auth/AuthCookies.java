package com.example.board.auth;

import com.example.board.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 인증 쿠키(액세스/리프레시) 읽기·쓰기 담당.
 * 두 토큰 모두 HttpOnly + SameSite=Lax 로 내려 JS 접근과 크로스 사이트 전송을 막는다.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public AuthCookies(@Value("${jwt.access-token-minutes}") long accessTokenMinutes,
                       @Value("${jwt.refresh-token-days}") long refreshTokenDays) {
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
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
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
