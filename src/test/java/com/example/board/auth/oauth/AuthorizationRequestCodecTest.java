package com.example.board.auth.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 인가 요청을 쿠키 값으로 옮기고 되돌리는 규칙.
 *
 * <p>지키려는 것은 둘이다 - <b>우리가 만든 값만 되돌아온다</b>(서명),
 * 그리고 <b>되돌아오는 것은 인가 요청 하나뿐이다</b>(임의 클래스가 만들어지지 않는다).</p>
 */
class AuthorizationRequestCodecTest {

    private static final String SECRET = "test-only-secret-key-must-be-32-bytes-min!";

    private final AuthorizationRequestCodec codec = new AuthorizationRequestCodec(SECRET);

    private OAuth2AuthorizationRequest request() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("client-id")
                .redirectUri("http://localhost:8080/login/oauth2/code/kakao")
                .scopes(Set.of("profile_nickname", "account_email"))
                .state("state-value")
                .attributes(Map.of("registration_id", "kakao"))
                .build();
    }

    @Test
    @DisplayName("담았다가 되돌리면 콜백에 필요한 값이 그대로 남는다")
    void roundTrip() {
        OAuth2AuthorizationRequest restored = codec.decode(codec.encode(request()));

        assertThat(restored).isNotNull();
        assertThat(restored.getState()).isEqualTo("state-value");
        assertThat(restored.getClientId()).isEqualTo("client-id");
        assertThat(restored.getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/kakao");
        assertThat(restored.getScopes()).containsExactlyInAnyOrder("profile_nickname", "account_email");
        // 이것이 빠지면 콜백에서 어느 제공자인지 알 수 없다
        assertThat(restored.getAttributes()).containsEntry("registration_id", "kakao");
    }

    @Test
    @DisplayName("내용을 바꿔치기하면 통과하지 못한다 - state 를 심어 남의 계정으로 로그인시키는 길을 막는다")
    void tamperedPayloadIsRejected() {
        String encoded = codec.encode(request());
        String signature = encoded.substring(encoded.indexOf('.'));
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"authorizationUri\":\"https://evil.example.com/authorize\",\"clientId\":\"client-id\","
                        + "\"redirectUri\":\"https://evil.example.com/cb\",\"scopes\":[],"
                        + "\"state\":\"attacker-state\",\"additionalParameters\":{},"
                        + "\"attributes\":{\"registration_id\":\"kakao\"}}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(codec.decode(forged + signature)).isNull();
    }

    @Test
    @DisplayName("서명만 떼어 내도 통과하지 못한다")
    void strippedSignatureIsRejected() {
        String encoded = codec.encode(request());

        assertThat(codec.decode(encoded.substring(0, encoded.indexOf('.') + 1))).isNull();
    }

    @Test
    @DisplayName("다른 비밀로 만든 값은 받아들이지 않는다")
    void foreignSignatureIsRejected() {
        String fromElsewhere = new AuthorizationRequestCodec("another-secret-key-of-sufficient-length!!")
                .encode(request());

        assertThat(codec.decode(fromElsewhere)).isNull();
    }

    @Test
    @DisplayName("형식이 깨진 값은 예외 대신 null - 로그인이 처음부터 다시 시작되면 된다")
    void malformedValuesReturnNull() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("서명이-없다")).isNull();
        assertThat(codec.decode("!!!.!!!")).isNull();
        assertThat(codec.decode(".")).isNull();
    }
}
