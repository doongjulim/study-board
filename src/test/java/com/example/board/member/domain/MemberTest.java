package com.example.board.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MemberTest {

    private Member member() {
        return new Member("tester1", "encoded-password", "동주");
    }

    @Test
    @DisplayName("가입하면 하루 목표 학습 시간이 기본값(30분)으로 시작한다")
    void defaultDailyGoal() {
        assertThat(member().getDailyGoalMinutes()).isEqualTo(Member.DEFAULT_DAILY_GOAL_MINUTES);
    }

    @Test
    @DisplayName("하루 목표 시간을 바꿀 수 있다")
    void changeDailyGoal() {
        Member member = member();

        member.changeDailyGoal(120);

        assertThat(member.getDailyGoalMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("0 으로 두면 시간 조건 없이 계획 완료만으로 판정한다")
    void allowsZeroToDisableGoal() {
        Member member = member();

        member.changeDailyGoal(0);

        assertThat(member.getDailyGoalMinutes()).isZero();
    }

    @Test
    @DisplayName("음수 목표는 설정할 수 없다")
    void rejectsNegativeGoal() {
        assertThatThrownBy(() -> member().changeDailyGoal(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("하루(1440분)를 넘는 목표는 달성할 수 없으므로 거부한다")
    void rejectsGoalLongerThanADay() {
        assertThatThrownBy(() -> member().changeDailyGoal(1441))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
