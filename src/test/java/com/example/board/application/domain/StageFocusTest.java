package com.example.board.application.domain;

import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StageFocusTest {

    @Test
    @DisplayName("지원은 있는데 이번 주 그 공부를 한 번도 하지 않았으면 따로 표시한다")
    void neglected() {
        assertThat(new StageFocus(PlanCategory.CODING_TEST, 3, 0).isNeglected()).isTrue();
        assertThat(new StageFocus(PlanCategory.CODING_TEST, 3, 30).isNeglected()).isFalse();
    }

    @Test
    @DisplayName("지원이 없으면 견줄 것도 없다 - 0시간이어도 경고가 아니다")
    void noApplicationsIsNotNeglect() {
        assertThat(new StageFocus(PlanCategory.INTERVIEW, 0, 0).isNeglected()).isFalse();
    }

    @Test
    @DisplayName("시간 표기는 공용 규칙을 따른다")
    void readable() {
        assertThat(new StageFocus(PlanCategory.INTERVIEW, 1, 90).readableStudied())
                .isEqualTo("1시간 30분");
    }
}
