package com.example.board.auth.oauth;

import java.util.Map;

/**
 * 제공자마다 다른 사용자 정보 응답을 우리가 쓰는 세 가지 값으로 줄인다.
 *
 * <p>구글은 평평한 JSON 을 주고, 카카오는 {@code kakao_account.profile.nickname} 처럼 두 겹 안에 넣는다.
 * 이 차이를 서비스나 핸들러가 알게 두면 제공자를 하나 더할 때마다 그 코드가 갈라진다.
 * 여기서만 안다.</p>
 *
 * @param providerId 그 제공자가 부여한 고유 id (제공자가 다르면 같은 값이라도 다른 사람이다)
 * @param email      주지 않을 수도 있다 (카카오는 이메일 동의가 선택이다)
 * @param nickname   없으면 부르는 쪽이 대신 만든다
 */
public record OAuthAttributes(String providerId, String email, String nickname) {

    /**
     * @param registrationId application.yml 의 등록 이름 (google, kakao)
     * @param attributes     제공자가 준 사용자 정보
     */
    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> ofKakao(attributes);
            case "google" -> ofGoogle(attributes);
            // 모르는 제공자를 조용히 통과시키면 providerId 가 비어 계정이 뒤섞인다
            default -> throw new IllegalArgumentException("지원하지 않는 로그인 제공자입니다: " + registrationId);
        };
    }

    private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        return new OAuthAttributes(
                string(attributes.get("sub")),
                string(attributes.get("email")),
                string(attributes.get("name")));
    }

    private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
        Map<String, Object> account = nested(attributes, "kakao_account");
        Map<String, Object> profile = nested(account, "profile");
        return new OAuthAttributes(
                string(attributes.get("id")),
                string(account.get("email")),
                string(profile.get("nickname")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return (value instanceof Map) ? (Map<String, Object>) value : Map.of();
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** 닉네임을 주지 않는 경우가 있다. 화면에 빈칸이 남지 않도록 대신 만든다 */
    public String nicknameOr(String fallback) {
        return (nickname == null || nickname.isBlank()) ? fallback : nickname;
    }
}
