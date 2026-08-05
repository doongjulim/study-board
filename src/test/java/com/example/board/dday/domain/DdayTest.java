package com.example.board.dday.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DdayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    private Member owner(long id) {
        Member member = new Member("tester" + id, "encoded-password", "테스터");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Dday dday(LocalDate targetDate) {
        return new Dday(owner(1L), "정보처리기사 실기", targetDate);
    }

    @Test
    @DisplayName("남은 날짜를 D-n 으로 표시한다")
    void labelBeforeTarget() {
        Dday dday = dday(TODAY.plusDays(7));

        assertThat(dday.remainingDays(TODAY)).isEqualTo(7);
        assertThat(dday.label(TODAY)).isEqualTo("D-7");
        assertThat(dday.isPast(TODAY)).isFalse();
    }

    @Test
    @DisplayName("목표 당일은 D-DAY 로 표시한다")
    void labelOnTarget() {
        Dday dday = dday(TODAY);

        assertThat(dday.remainingDays(TODAY)).isZero();
        assertThat(dday.label(TODAY)).isEqualTo("D-DAY");
        assertThat(dday.isPast(TODAY)).isFalse();
    }

    @Test
    @DisplayName("지난 목표는 D+n 으로 표시하고 지난 것으로 처리한다")
    void labelAfterTarget() {
        Dday dday = dday(TODAY.minusDays(3));

        assertThat(dday.remainingDays(TODAY)).isEqualTo(-3);
        assertThat(dday.label(TODAY)).isEqualTo("D+3");
        assertThat(dday.isPast(TODAY)).isTrue();
    }

    @Test
    @DisplayName("소유자만 본인 것으로 판단한다")
    void ownership() {
        Dday dday = dday(TODAY);

        assertThat(dday.isOwnedBy(1L)).isTrue();
        assertThat(dday.isOwnedBy(999L)).isFalse();
    }

    @Test
    @DisplayName("update 로 제목과 목표일을 바꿀 수 있다")
    void update() {
        Dday dday = dday(TODAY);

        dday.update("최종 면접", TODAY.plusDays(10));

        assertThat(dday.getTitle()).isEqualTo("최종 면접");
        assertThat(dday.getTargetDate()).isEqualTo(TODAY.plusDays(10));
    }
}
