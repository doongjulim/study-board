package com.example.board.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** JWT 액세스 토큰 발급/검증. 서명 키와 만료 시간은 설정(jwt.*)으로 주입받는다. */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration validity;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-minutes}") long accessTokenMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofMinutes(accessTokenMinutes);
    }

    public String createToken(Long memberId, String loginId, String nickname) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("loginId", loginId)
                .claim("nickname", nickname)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** 유효하지 않은 토큰(만료·변조·형식 오류)은 빈 Optional 로 처리해 익명 요청으로 흘려보낸다 */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get("loginId", String.class),
                    claims.get("nickname", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenClaims(Long memberId, String loginId, String nickname) {
    }
}
