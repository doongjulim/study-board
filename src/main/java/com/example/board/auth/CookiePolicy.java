package com.example.board.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 이 애플리케이션이 쿠키를 내려보내는 <b>한 가지 방식</b>.
 *
 * <p>속성을 곳곳에서 각자 정하면 언젠가 하나가 빠진다. 실제로 그랬다 -
 * 인증 쿠키는 {@code ResponseCookie} 로 {@code SameSite=Lax} 를 명시하는데,
 * 소셜 로그인의 인가 요청 쿠키만 raw {@code Cookie} 를 써서 SameSite 가 없었다.
 * 정책이 두 갈래면 어느 쪽이 맞는지 코드만 봐서는 알 수 없다.</p>
 *
 * <ul>
 *   <li><b>HttpOnly</b> - 화면 스크립트가 읽지 못한다. XSS 가 한 번 나도 쿠키까지 넘어가지는 않는다</li>
 *   <li><b>SameSite=Lax</b> - 다른 사이트가 보낸 요청에는 실리지 않는다.
 *       다만 톱레벨 이동(주소창이 바뀌는 이동)에는 실리므로, 제공자에서 돌아오는
 *       소셜 로그인 콜백은 그대로 동작한다. Strict 로 두면 그 콜백이 깨진다</li>
 *   <li><b>Secure</b> - 설정값이다. 로컬은 http 라 켜면 쿠키가 아예 안 실리고,
 *       운영은 https 이므로 켜야 한다. 코드가 아니라 환경이 정할 일이다</li>
 * </ul>
 */
@Component
public class CookiePolicy {

    private final boolean secure;

    public CookiePolicy(@Value("${app.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    /** {@code Set-Cookie} 헤더 값 하나를 만든다 */
    public String build(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    /** 즉시 만료시키는 헤더 값 - 지우는 쪽도 같은 속성이어야 브라우저가 같은 쿠키로 알아본다 */
    public String expire(String name) {
        return build(name, "", 0);
    }
}
