package com.example.board.auth.jwt;

import com.example.board.auth.MemberPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-only-secret-key-must-be-32-bytes-min!";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60);

    @Test
    @DisplayName("발급한 토큰을 파싱하면 담았던 회원 정보가 그대로 나온다")
    void createAndParse() {
        String token = provider.createToken(1L, "tester", "테스터");

        Optional<MemberPrincipal> claims = provider.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().id()).isEqualTo(1L);
        assertThat(claims.get().loginId()).isEqualTo("tester");
        assertThat(claims.get().nickname()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("만료된 토큰은 빈 Optional 을 반환한다")
    void parse_expiredToken() {
        JwtTokenProvider zeroMinuteProvider = new JwtTokenProvider(SECRET, 0);
        String token = zeroMinuteProvider.createToken(1L, "tester", "테스터");

        assertThat(zeroMinuteProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 빈 Optional 을 반환한다")
    void parse_tamperedToken() {
        JwtTokenProvider otherKeyProvider =
                new JwtTokenProvider("another-secret-key-that-is-32-bytes-long!", 60);
        String token = otherKeyProvider.createToken(1L, "tester", "테스터");

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 형식이 아닌 문자열은 빈 Optional 을 반환한다")
    void parse_garbage() {
        assertThat(provider.parse("not-a-jwt")).isEmpty();
        assertThat(provider.parse("")).isEmpty();
    }
}
