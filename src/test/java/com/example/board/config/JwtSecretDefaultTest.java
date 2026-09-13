package com.example.board.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * 운영 설정에는 JWT 시크릿의 기본값이 없어야 한다.
 *
 * <p>예전에는 {@code ${JWT_SECRET:study-board-local-dev-...}} 였다. 환경변수를 한 번 빠뜨리면
 * 앱은 아무 불평 없이 뜨고, <b>공개 저장소에 적혀 있는 문자열로</b> 토큰에 서명한다 -
 * 저장소를 읽을 수 있는 사람은 누구나 아무 회원의 토큰을 위조할 수 있다.</p>
 *
 * <p>개발 편의는 local 프로필이 맡는다. 이 테스트는 편의가 다시 기본 설정으로 새어 나오는 것을 막는다 -
 * 사람이 "잠깐만 편하게" 되돌려 놓기 가장 쉬운 자리이기 때문이다.</p>
 */
class JwtSecretDefaultTest {

    private String read(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("application.yml 의 jwt.secret 에는 기본값이 없다")
    void baseConfigHasNoDefault() throws IOException {
        assertThat(read("application.yml"))
                .contains("secret: ${JWT_SECRET}")
                .doesNotContain("${JWT_SECRET:");
    }

    @Test
    @DisplayName("개발용 시크릿은 local 프로필에만 있다")
    void devSecretLivesOnlyInLocalProfile() throws IOException {
        String devSecret = "study-board-local-dev-secret-key-please-override";

        assertThat(read("application-local.yml")).contains(devSecret);
        assertThat(read("application.yml")).doesNotContain(devSecret);
    }

    @Test
    @DisplayName("프로필을 지정하지 않으면 local 이 켜진다 - 받아서 바로 실행해도 뜨게")
    void defaultProfileIsLocal() throws IOException {
        assertThat(read("application.yml")).contains("default: local");
    }
}
