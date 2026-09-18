package com.example.board.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분 표기 규칙. 통계와 플래너가 함께 쓰므로 여기 한 벌만 있으면 된다.
 *
 * <p>날짜 포맷이 열두 가지로 갈라졌던 전례가 있어, 규칙을 옮기면서 시험도 함께 옮겼다.</p>
 */
class ReadableDurationTest {

    @Test
    @DisplayName("한 시간 미만은 분으로 적는다")
    void underAnHour() {
        assertThat(ReadableDuration.of(0)).isEqualTo("0분");
        assertThat(ReadableDuration.of(30)).isEqualTo("30분");
        assertThat(ReadableDuration.of(59)).isEqualTo("59분");
    }

    @Test
    @DisplayName("딱 떨어지면 분을 적지 않는다 - '2시간 0분' 은 사람이 쓰는 말이 아니다")
    void wholeHours() {
        assertThat(ReadableDuration.of(60)).isEqualTo("1시간");
        assertThat(ReadableDuration.of(120)).isEqualTo("2시간");
    }

    @Test
    @DisplayName("남는 분은 시간 뒤에 붙인다")
    void hoursAndMinutes() {
        assertThat(ReadableDuration.of(90)).isEqualTo("1시간 30분");
        assertThat(ReadableDuration.of(200)).isEqualTo("3시간 20분");
    }
}
