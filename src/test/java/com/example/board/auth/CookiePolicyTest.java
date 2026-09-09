package com.example.board.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 쿠키를 내려보내는 방식은 한 곳에서 정한다.
 *
 * <p>속성을 곳곳에서 각자 정하던 때에 소셜 로그인의 인가 요청 쿠키만 SameSite 가 빠져 있었다.
 * 여기가 그 정책의 유일한 자리이므로, 정책이 바뀌면 이 테스트가 먼저 깨져야 한다.</p>
 */
class CookiePolicyTest {

    @Test
    @DisplayName("모든 쿠키에 HttpOnly · SameSite=Lax · Path=/ 가 붙는다")
    void appliesProjectDefaults() {
        String header = new CookiePolicy(false).build("SOME_TOKEN", "value", 900);

        assertThat(header)
                .contains("SOME_TOKEN=value")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/")
                .contains("Max-Age=900");
    }

    @Test
    @DisplayName("로컬(http)에서는 Secure 를 붙이지 않는다 - 붙이면 쿠키가 아예 실리지 않는다")
    void omitsSecureWhenDisabled() {
        assertThat(new CookiePolicy(false).build("SOME_TOKEN", "value", 900))
                .doesNotContain("Secure");
    }

    @Test
    @DisplayName("운영(https)에서는 Secure 를 붙인다 - 설정이 정한다")
    void addsSecureWhenEnabled() {
        assertThat(new CookiePolicy(true).build("SOME_TOKEN", "value", 900))
                .contains("Secure");
    }

    @Test
    @DisplayName("지우는 쿠키도 같은 속성이어야 브라우저가 같은 쿠키로 알아본다")
    void expireKeepsSameAttributes() {
        String header = new CookiePolicy(true).expire("SOME_TOKEN");

        assertThat(header)
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure")
                .contains("Path=/");
    }
}
