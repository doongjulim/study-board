package com.example.board.group.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.*;

@DisplayName("초대 코드")
class InviteCodeTest {

    @Test
    @DisplayName("8자리이고, 헷갈리는 글자(0/O·1/I/L)가 없는 글자만 쓴다")
    void usesOnlyUnambiguousCharacters() {
        String code = InviteCode.generate(new Random(42));

        assertThat(code).hasSize(InviteCode.LENGTH);
        for (char c : code.toCharArray()) {
            assertThat(InviteCode.ALPHABET).contains(String.valueOf(c));
        }
        assertThat(code).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    @DisplayName("시드가 같으면 같은 코드가 나온다 - 생성이 Random 에만 의존한다는 보증")
    void deterministicWithSameSeed() {
        assertThat(InviteCode.generate(new Random(7)))
                .isEqualTo(InviteCode.generate(new Random(7)));
    }

    @Test
    @DisplayName("시드가 다르면 다른 코드가 나온다")
    void differentSeedsProduceDifferentCodes() {
        assertThat(InviteCode.generate(new Random(1)))
                .isNotEqualTo(InviteCode.generate(new Random(2)));
    }
}
