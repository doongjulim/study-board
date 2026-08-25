package com.example.board.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 토큰 생성과 해싱.
 *
 * <p>리프레시 토큰과 비밀번호 재설정 토큰이 같은 규칙(원문은 사용자에게만, DB 에는 SHA-256 해시만)을
 * 쓰므로 한곳에 모은다. DB 가 유출되어도 저장된 값으로는 인증을 통과할 수 없다.</p>
 *
 * <p>비밀번호와 달리 BCrypt 를 쓰지 않는 이유: 이 토큰들은 128비트 이상의 임의 값이라
 * 사전 공격 대상이 아니고, 요청마다 조회해야 해서 느린 해시가 오히려 병목이 된다.</p>
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** 추측할 수 없는 토큰 원문을 만든다 */
    public static String newToken() {
        return UUID.randomUUID().toString();
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
