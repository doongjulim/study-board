package com.example.board.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenHasher")
class TokenHasherTest {

    @Test
    @DisplayName("같은 원문은 항상 같은 해시가 된다 (조회로 찾을 수 있어야 하므로)")
    void deterministic() {
        assertThat(TokenHasher.hash("some-token")).isEqualTo(TokenHasher.hash("some-token"));
    }

    @Test
    @DisplayName("다른 원문은 다른 해시가 된다")
    void distinct() {
        assertThat(TokenHasher.hash("token-a")).isNotEqualTo(TokenHasher.hash("token-b"));
    }

    @Test
    @DisplayName("해시에는 원문이 남지 않는다")
    void doesNotLeakRawValue() {
        String raw = "super-secret-token";

        assertThat(TokenHasher.hash(raw)).doesNotContain(raw);
    }

    @Test
    @DisplayName("SHA-256 이므로 16진수 64자다 (컬럼 길이와 맞아야 한다)")
    void hexLength() {
        assertThat(TokenHasher.hash("anything")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("발급할 때마다 다른 토큰이 나온다")
    void newTokenIsUnique() {
        assertThat(TokenHasher.newToken()).isNotEqualTo(TokenHasher.newToken());
    }
}
