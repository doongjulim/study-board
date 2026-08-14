package com.example.board.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlanSearchCondition")
class PlanSearchConditionTest {

    private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);

    @Nested
    @DisplayName("검색어")
    class Keyword {

        @Test
        @DisplayName("공백만 있는 검색어는 조건으로 치지 않는다")
        void blankIsNoCondition() {
            PlanSearchCondition condition = PlanSearchCondition.of("   ", null, null, null, null);

            assertThat(condition.hasKeyword()).isFalse();
            assertThat(condition.keyword()).isNull();
        }

        @Test
        @DisplayName("앞뒤 공백은 잘라낸다")
        void trims() {
            PlanSearchCondition condition = PlanSearchCondition.of("  알고리즘  ", null, null, null, null);

            assertThat(condition.keyword()).isEqualTo("알고리즘");
            assertThat(condition.hasKeyword()).isTrue();
        }
    }

    @Nested
    @DisplayName("기간")
    class Period {

        @Test
        @DisplayName("시작이 끝보다 늦으면 뒤집어 준다 (빈 결과 대신)")
        void swapsReversedRange() {
            PlanSearchCondition condition = PlanSearchCondition.of(null, null, null, AUGUST, MAY);

            assertThat(condition.from()).isEqualTo(MAY);
            assertThat(condition.to()).isEqualTo(AUGUST);
        }

        @Test
        @DisplayName("한쪽만 넣어도 그대로 둔다")
        void keepsOpenEndedRange() {
            PlanSearchCondition condition = PlanSearchCondition.of(null, null, null, MAY, null);

            assertThat(condition.from()).isEqualTo(MAY);
            assertThat(condition.to()).isNull();
        }
    }

    @Nested
    @DisplayName("완료 여부")
    class Status {

        @Test
        @DisplayName("지정하지 않으면 전체다")
        void defaultsToAll() {
            PlanSearchCondition condition = PlanSearchCondition.of(null, null, null, null, null);

            assertThat(condition.status()).isEqualTo(PlanStatus.ALL);
            assertThat(condition.completedFilter()).isNull();
        }

        @Test
        @DisplayName("남은 것만 보려면 완료 여부를 false 로 건다")
        void todoFiltersIncomplete() {
            PlanSearchCondition condition =
                    PlanSearchCondition.of(null, null, PlanStatus.TODO, null, null);

            assertThat(condition.completedFilter()).isFalse();
        }

        @Test
        @DisplayName("끝낸 것만 보려면 완료 여부를 true 로 건다")
        void doneFiltersCompleted() {
            PlanSearchCondition condition =
                    PlanSearchCondition.of(null, null, PlanStatus.DONE, null, null);

            assertThat(condition.completedFilter()).isTrue();
        }
    }

    @Test
    @DisplayName("아무 조건도 없는 상태를 구분한다")
    void isEmpty() {
        assertThat(PlanSearchCondition.of(" ", null, null, null, null).isEmpty()).isTrue();
        assertThat(PlanSearchCondition.of("알고리즘", null, null, null, null).isEmpty()).isFalse();
        assertThat(PlanSearchCondition.of(null, PlanCategory.RESUME, null, null, null).isEmpty()).isFalse();
        assertThat(PlanSearchCondition.of(null, null, PlanStatus.TODO, null, null).isEmpty()).isFalse();
        assertThat(PlanSearchCondition.of(null, null, null, MAY, null).isEmpty()).isFalse();
    }
}
