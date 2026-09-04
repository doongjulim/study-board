package com.example.board.auth.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제공자마다 응답 모양이 다르다. 그 차이를 아는 곳은 여기 하나여야 한다.
 * 값 객체라 네트워크 없이 규칙을 전부 확인할 수 있다.
 */
class OAuthAttributesTest {

    @Nested
    @DisplayName("구글")
    class Google {

        @Test
        @DisplayName("평평한 응답에서 sub·email·name 을 읽는다")
        void reads() {
            OAuthAttributes attributes = OAuthAttributes.of("google", Map.of(
                    "sub", "1234567890", "email", "dj@example.com", "name", "동주"));

            assertThat(attributes.providerId()).isEqualTo("1234567890");
            assertThat(attributes.email()).isEqualTo("dj@example.com");
            assertThat(attributes.nickname()).isEqualTo("동주");
        }
    }

    @Nested
    @DisplayName("카카오")
    class Kakao {

        @Test
        @DisplayName("두 겹 안에 있는 이메일·닉네임을 꺼낸다")
        void readsNested() {
            OAuthAttributes attributes = OAuthAttributes.of("kakao", Map.of(
                    "id", 987654321L,
                    "kakao_account", Map.of(
                            "email", "dj@kakao.com",
                            "profile", Map.of("nickname", "동주"))));

            assertThat(attributes.providerId()).isEqualTo("987654321");
            assertThat(attributes.email()).isEqualTo("dj@kakao.com");
            assertThat(attributes.nickname()).isEqualTo("동주");
        }

        @Test
        @DisplayName("이메일 동의를 거부하면 이메일 없이 온다 - 그래도 로그인은 되어야 한다")
        void emailIsOptional() {
            OAuthAttributes attributes = OAuthAttributes.of("kakao", Map.of(
                    "id", 987654321L,
                    "kakao_account", Map.of("profile", Map.of("nickname", "동주"))));

            assertThat(attributes.email()).isNull();
            assertThat(attributes.providerId()).isEqualTo("987654321");
        }

        @Test
        @DisplayName("동의 항목이 전부 빠져 kakao_account 자체가 없어도 터지지 않는다")
        void missingAccountBlock() {
            OAuthAttributes attributes = OAuthAttributes.of("kakao", Map.of("id", 1L));

            assertThat(attributes.providerId()).isEqualTo("1");
            assertThat(attributes.email()).isNull();
            assertThat(attributes.nickname()).isNull();
        }
    }

    @Test
    @DisplayName("모르는 제공자는 거절한다 - 조용히 통과시키면 providerId 가 비어 계정이 뒤섞인다")
    void unknownProvider() {
        assertThatThrownBy(() -> OAuthAttributes.of("naver", Map.of("id", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 문자열은 없는 값으로 본다 - 빈 닉네임이 화면에 그대로 나가지 않게")
    void blankBecomesNull() {
        OAuthAttributes attributes = OAuthAttributes.of("google", Map.of(
                "sub", "1", "email", "  ", "name", ""));

        assertThat(attributes.email()).isNull();
        assertThat(attributes.nickname()).isNull();
        assertThat(attributes.nicknameOr("구글 사용자")).isEqualTo("구글 사용자");
    }

    @Test
    @DisplayName("로그인 아이디는 제공자를 접두어로 붙인다 - 일반 가입 아이디와 부딪히지 않게")
    void loginIdIsNamespaced() {
        OAuthAttributes attributes = OAuthAttributes.of("google", Map.of("sub", "1234"));

        assertThat(attributes.toLoginId("google")).isEqualTo("google_1234");
    }
}
