package com.example.board.onboarding.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OnboardingProgress")
class OnboardingProgressTest {

    /** (오늘 할 일, 목표일, 목표일 건너뜀) */
    private OnboardingProgress progress(boolean planCreated, boolean ddayCreated) {
        return new OnboardingProgress(planCreated, ddayCreated, false);
    }

    @Test
    @DisplayName("아무것도 하지 않았으면 오늘 할 일(1단계)부터 시작한다 - 첫 질문이 '목표가 뭔가요?' 이면 안 된다")
    void startsAtFirstStep() {
        OnboardingProgress progress = progress(false, false);

        assertThat(progress.currentStep()).isEqualTo(1);
        assertThat(progress.completedSteps()).isZero();
        assertThat(progress.percent()).isZero();
    }

    @Test
    @DisplayName("오늘 할 일을 적었으면 목표일(2단계)로 넘어간다")
    void movesToSecondStep() {
        OnboardingProgress progress = progress(true, false);

        assertThat(progress.currentStep()).isEqualTo(2);
        assertThat(progress.completedSteps()).isEqualTo(1);
        assertThat(progress.percent()).isEqualTo(33);
    }

    @Test
    @DisplayName("목표일까지 정했으면 마지막 타이머 체험(3단계)이다")
    void movesToLastStep() {
        OnboardingProgress progress = progress(true, true);

        assertThat(progress.currentStep()).isEqualTo(3);
        assertThat(progress.isLastStep()).isTrue();
        assertThat(progress.completedSteps()).isEqualTo(2);
    }

    @Test
    @DisplayName("목표일이 있어도 오늘 할 일이 없으면 1단계다 - 첫 성공을 건너뛸 수 없다")
    void planComesFirst() {
        OnboardingProgress progress = progress(false, true);

        assertThat(progress.currentStep()).isEqualTo(1);
        assertThat(progress.isLastStep()).isFalse();
    }

    @Test
    @DisplayName("목표일을 건너뛰면 마지막 단계로 간다 - 시험일이 안 정해진 사람을 붙잡아 두지 않는다")
    void skippedGoalMovesOn() {
        OnboardingProgress progress = new OnboardingProgress(true, false, true);

        assertThat(progress.currentStep()).isEqualTo(3);
        assertThat(progress.isLastStep()).isTrue();
    }

    @Test
    @DisplayName("건너뛴 목표일은 '한 것' 으로 세지 않는다 - 체크 표시는 실제로 한 것에만 붙는다")
    void skippedGoalIsNotCompleted() {
        OnboardingProgress progress = new OnboardingProgress(true, false, true);

        assertThat(progress.completedSteps()).isEqualTo(1);
        assertThat(progress.ddayCreated()).isFalse();
    }

    @Test
    @DisplayName("건너뛰겠다고 해도 오늘 할 일이 없으면 여전히 1단계다")
    void skipDoesNotJumpOverFirstStep() {
        OnboardingProgress progress = new OnboardingProgress(false, false, true);

        assertThat(progress.currentStep()).isEqualTo(1);
    }

    @Test
    @DisplayName("isStep 으로 화면이 어느 단계를 그릴지 판단한다")
    void isStep() {
        OnboardingProgress progress = progress(true, false);

        assertThat(progress.isStep(1)).isFalse();
        assertThat(progress.isStep(2)).isTrue();
        assertThat(progress.isStep(3)).isFalse();
    }
}
