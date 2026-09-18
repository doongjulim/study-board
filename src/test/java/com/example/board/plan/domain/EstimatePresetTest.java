package com.example.board.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstimatePresetTest {

    @Test
    @DisplayName("선택지는 모두 Plan 이 받아들이는 범위 안에 있다 - 화면에 있는 버튼이 눌러서 실패하면 안 된다")
    void withinDomainLimit() {
        for (EstimatePreset preset : EstimatePreset.values()) {
            assertThat(preset.getMinutes()).isBetween(1, Plan.MAX_ESTIMATED_MINUTES);
        }
    }

    @Test
    @DisplayName("이름은 공용 표기 규칙을 따른다 - 어느 화면은 '60분', 어느 화면은 '1시간' 이 되지 않게")
    void labelFollowsSharedFormat() {
        assertThat(EstimatePreset.HALF_HOUR.getLabel()).isEqualTo("30분");
        assertThat(EstimatePreset.ONE_HOUR.getLabel()).isEqualTo("1시간");
        assertThat(EstimatePreset.TWO_HOURS.getLabel()).isEqualTo("2시간");
    }
}
