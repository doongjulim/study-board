package com.example.board.retro.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("회고")
class RetrospectiveTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    private Member owner() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    @DisplayName("주간 회고는 수요일에 써도 그 주 월요일 것으로 저장된다")
    void weeklyIsStoredOnMonday() {
        Retrospective retro = Retrospective.write(owner(), RetroType.WEEKLY, WEDNESDAY, "계획을 과하게 잡았다");

        assertThat(retro.getTargetDate()).isEqualTo(MONDAY);
    }

    @Test
    @DisplayName("하루 회고는 그날 날짜로 저장된다")
    void dailyIsStoredOnItsDay() {
        Retrospective retro = Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "오전에 집중이 잘 됐다");

        assertThat(retro.getTargetDate()).isEqualTo(WEDNESDAY);
    }

    @Test
    @DisplayName("앞뒤 공백은 지우고 저장한다")
    void stripsContent() {
        Retrospective retro = Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "  집중 잘 됨  ");

        assertThat(retro.getContent()).isEqualTo("집중 잘 됨");
    }

    @Test
    @DisplayName("빈 회고는 저장되지 않는다")
    void rejectsBlank() {
        assertThatThrownBy(() -> Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주기별 길이 제한을 넘기면 거절한다")
    void rejectsTooLong() {
        assertThatThrownBy(() -> Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("회고는 쌓지 않고 고쳐 쓴다")
    void editReplacesContent() {
        Retrospective retro = Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "처음 생각");

        retro.edit("다시 생각해 보니 이랬다");

        assertThat(retro.getContent()).isEqualTo("다시 생각해 보니 이랬다");
    }

    @Test
    @DisplayName("고쳐 쓸 때도 같은 길이 규칙이 걸린다")
    void editKeepsTheSameRule() {
        Retrospective retro = Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "처음 생각");

        assertThatThrownBy(() -> retro.edit(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("본인 것인지 확인할 수 있다")
    void knowsItsOwner() {
        Retrospective retro = Retrospective.write(owner(), RetroType.DAILY, WEDNESDAY, "메모");

        assertThat(retro.isOwnedBy(1L)).isTrue();
        assertThat(retro.isOwnedBy(2L)).isFalse();
    }
}
