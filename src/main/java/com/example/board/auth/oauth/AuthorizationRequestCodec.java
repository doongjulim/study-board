package com.example.board.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 인가 요청을 쿠키에 담을 문자열로 바꾸고, 되돌린다.
 *
 * <p>― <b>왜 자바 직렬화를 쓰지 않는가</b><br>
 * 쿠키는 브라우저가 보내는 값이다. 즉 <b>공격자가 정할 수 있는 입력</b>이다.
 * 그것을 {@code SerializationUtils.deserialize} 로 되돌리면 임의의 클래스를 인스턴스로 만들 수 있는
 * 통로가 된다 - Spring 6 에서 그 메서드가 deprecated 된 이유가 정확히 이것이다.
 * 지금 클래스패스에 알려진 가젯이 없더라도, 의존성이 하나 늘 때마다 그 전제가 다시 바뀐다.</p>
 *
 * <p>그래서 <b>필요한 값만 JSON 으로</b> 옮긴다. 되돌릴 때는 우리가 아는 필드만 읽어
 * {@link OAuth2AuthorizationRequest} 를 다시 세우므로, 쿠키에 무엇이 들어 있든
 * 만들어지는 객체는 이 한 종류뿐이다.</p>
 *
 * <p>― <b>왜 서명하는가</b><br>
 * state 는 "내가 보낸 요청이 맞는가" 를 확인하는 값인데, 그 state 자체가 쿠키에 들어 있다.
 * 쿠키를 바꿔 넣을 수 있으면 공격자가 자기 state 를 심어 <b>피해자를 공격자 계정으로 로그인</b>시킬 수 있다.
 * HMAC 을 붙여 두면 우리가 만들지 않은 값은 통과하지 못한다.</p>
 *
 * <p>서명 키는 JWT 서명 키를 그대로 쓰지 않고 용도 라벨을 섞어 파생시킨다 -
 * 하나의 키를 두 목적으로 쓰면 한쪽의 약점이 다른 쪽으로 옮겨 간다.</p>
 */
@Component
public class AuthorizationRequestCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 키 파생 라벨. 같은 비밀에서 나왔지만 용도가 다른 키가 된다 */
    private static final String KEY_LABEL = "oauth2-authorization-request";
    private static final char SEPARATOR = '.';

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretKeySpec signingKey;

    public AuthorizationRequestCodec(@Value("${jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(
                hmac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM),
                        KEY_LABEL.getBytes(StandardCharsets.UTF_8)),
                HMAC_ALGORITHM);
    }

    /** 쿠키에 담을 값: {@code base64url(json).base64url(hmac)} */
    public String encode(OAuth2AuthorizationRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(Payload.from(request));
            return encodeBase64(json) + SEPARATOR + encodeBase64(hmac(signingKey, json));
        } catch (JsonProcessingException e) {
            // 우리가 만든 값만 들어오므로 실패할 이유가 없다. 조용히 넘기면 원인을 못 찾는다
            throw new IllegalStateException("인가 요청을 직렬화하지 못했습니다.", e);
        }
    }

    /**
     * 되돌린다. 서명이 맞지 않거나 형식이 깨졌으면 {@code null} -
     * 부르는 쪽은 "그런 요청이 없다" 로 보고 로그인을 처음부터 다시 시작하면 된다.
     */
    public OAuth2AuthorizationRequest decode(String value) {
        if (value == null) {
            return null;
        }
        int separator = value.indexOf(SEPARATOR);
        if (separator < 0) {
            return null;
        }
        try {
            byte[] json = decodeBase64(value.substring(0, separator));
            byte[] signature = decodeBase64(value.substring(separator + 1));
            // 길이가 다르면 바로 false, 같으면 끝까지 비교한다 - 앞에서 끊지 않는다
            if (!MessageDigest.isEqual(hmac(signingKey, json), signature)) {
                return null;
            }
            return objectMapper.readValue(json, Payload.class).toRequest();
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /**
     * 쿠키에 실어 나르는 값들. {@link OAuth2AuthorizationRequest} 를 다시 세우는 데 필요한 것만 담는다.
     *
     * <p>{@code attributes} 에 registration_id 가 들어 있다 - 이것이 빠지면 콜백에서
     * 어느 제공자인지 알 수 없다.</p>
     */
    record Payload(String authorizationUri, String clientId, String redirectUri,
                   Set<String> scopes, String state,
                   Map<String, Object> additionalParameters, Map<String, Object> attributes) {

        static Payload from(OAuth2AuthorizationRequest request) {
            return new Payload(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    new LinkedHashSet<>(request.getScopes()),
                    request.getState(),
                    new LinkedHashMap<>(request.getAdditionalParameters()),
                    new LinkedHashMap<>(request.getAttributes()));
        }

        OAuth2AuthorizationRequest toRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes == null ? Set.of() : scopes)
                    .state(state)
                    .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
                    .attributes(attributes == null ? Map.of() : attributes)
                    .build();
        }
    }

    private static String encodeBase64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static byte[] hmac(SecretKeySpec key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 을 사용할 수 없습니다.", e);
        }
    }
}
