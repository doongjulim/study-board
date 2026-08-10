package com.example.board.home.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoalProgress 는 오늘 목표 대비 진행도를 계산한다")
class GoalProgressTest {

    @Nested
    @DisplayName("달성률")
    class Percent {

        @Test
        @DisplayName("목표 60분 중 30분을 공부했으면 50% 다")
        void half() {
            assertThat(GoalProgress.of(30, 60).percent()).isEqualTo(50);
        }

        @Test
        @DisplayName("목표를 넘겨도 링은 100% 에서 멈춘다")
        void cappedAtHundred() {
            GoalProgress progress = GoalProgress.of(200, 60);

            assertThat(progress.percent()).isEqualTo(100);
            assertThat(progress.reached()).isTrue();
        }

        @Test
        @DisplayName("아직 공부하지 않았으면 0% 다")
        void nothingYet() {
            GoalProgress progress = GoalProgress.of(0, 60);

            assertThat(progress.percent()).isZero();
            assertThat(progress.reached()).isFalse();
        }

        @Test
        @DisplayName("목표를 정확히 채우면 달성이다")
        void exactlyReached() {
            assertThat(GoalProgress.of(60, 60).reached()).isTrue();
        }
    }

    @Nested
    @DisplayName("목표를 꺼 둔 경우")
    class GoalDisabled {

        @Test
        @DisplayName("목표가 0 이면 링은 공부한 순간 100% 로 채워진다")
        void studiedWithoutGoal() {
            GoalProgress progress = GoalProgress.of(45, 0);

            assertThat(progress.hasGoal()).isFalse();
            assertThat(progress.percent()).isEqualTo(100);
            assertThat(progress.reached()).isTrue();
        }

        @Test
        @DisplayName("목표도 없고 공부도 안 했으면 0% 다")
        void nothingAtAll() {
            GoalProgress progress = GoalProgress.of(0, 0);

            assertThat(progress.percent()).isZero();
            assertThat(progress.reached()).isFalse();
        }
    }

    @Nested
    @DisplayName("남은 시간")
    class Remaining {

        @Test
        @DisplayName("목표까지 남은 분을 알려 준다")
        void remainingMinutes() {
            assertThat(GoalProgress.of(20, 60).remainingMinutes()).isEqualTo(40);
        }

        @Test
        @DisplayName("이미 채웠으면 남은 시간은 0 이다 (음수가 되지 않는다)")
        void neverNegative() {
            assertThat(GoalProgress.of(90, 60).remainingMinutes()).isZero();
        }
    }

    @Nested
    @DisplayName("시:분 표기")
    class Formatting {

        @Test
        @DisplayName("공부한 시간을 시간과 분으로 나눠 준다")
        void splitsHoursAndMinutes() {
            GoalProgress progress = GoalProgress.of(130, 180);

            assertThat(progress.actualHours()).isEqualTo(2);
            assertThat(progress.actualRemainderMinutes()).isEqualTo(10);
        }

        @Test
        @DisplayName("한 시간이 안 되면 시간은 0 이다")
        void underAnHour() {
            GoalProgress progress = GoalProgress.of(45, 60);

            assertThat(progress.actualHours()).isZero();
            assertThat(progress.actualRemainderMinutes()).isEqualTo(45);
        }
    }
}
