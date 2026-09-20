package com.example.board.session.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PomodoroTest {

    @Test
    @DisplayName("네 번째 집중 뒤에는 긴 휴식이다 - 짧은 휴식만으로는 반나절을 버티지 못한다")
    void longBreakEveryFourth() {
        assertThat(Pomodoro.breakAfter(1)).isEqualTo(Pomodoro.SHORT_BREAK);
        assertThat(Pomodoro.breakAfter(2)).isEqualTo(Pomodoro.SHORT_BREAK);
        assertThat(Pomodoro.breakAfter(3)).isEqualTo(Pomodoro.SHORT_BREAK);
        assertThat(Pomodoro.breakAfter(4)).isEqualTo(Pomodoro.LONG_BREAK);
        assertThat(Pomodoro.breakAfter(8)).isEqualTo(Pomodoro.LONG_BREAK);
    }

    @Test
    @DisplayName("집중을 한 번도 마치지 않았는데 휴식을 묻는 것은 잘못된 호출이다")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> Pomodoro.breakAfter(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("집중 완료는 경과 시간으로 판단한다 - 24분 59초는 아직 아니다")
    void focusCompleted() {
        assertThat(Pomodoro.focusCompleted(Pomodoro.FOCUS.toSeconds() - 1)).isFalse();
        assertThat(Pomodoro.focusCompleted(Pomodoro.FOCUS.toSeconds())).isTrue();
    }

    @Test
    @DisplayName("정책은 방치 판정(6시간)보다 짧아야 한다 - 한 번의 집중이 방치로 잘리면 안 된다")
    void focusFitsWithinSessionLimit() {
        assertThat(Pomodoro.FOCUS).isLessThan(StudySession.MAX_DURATION);
        assertThat(Pomodoro.FOCUS).isGreaterThan(Duration.ZERO);
        assertThat(Pomodoro.LONG_BREAK).isGreaterThan(Pomodoro.SHORT_BREAK);
    }
}
