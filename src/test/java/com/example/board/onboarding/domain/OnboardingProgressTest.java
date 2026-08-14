package com.example.board.onboarding.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OnboardingProgress")
class OnboardingProgressTest {

    @Test
    @DisplayName("아무것도 하지 않았으면 목표일 등록(1단계)부터 시작한다")
    void startsAtFirstStep() {
        OnboardingProgress progress = new OnboardingProgress(false, false);

        assertThat(progress.currentStep()).isEqualTo(1);
        assertThat(progress.completedSteps()).isZero();
        assertThat(progress.percent()).isZero();
    }

    @Test
    @DisplayName("목표일을 등록했으면 오늘 계획(2단계)으로 넘어간다")
    void movesToSecondStep() {
        OnboardingProgress progress = new OnboardingProgress(true, false);

        assertThat(progress.currentStep()).isEqualTo(2);
        assertThat(progress.completedSteps()).isEqualTo(1);
        assertThat(progress.percent()).isEqualTo(33);
    }

    @Test
    @DisplayName("계획까지 세웠으면 마지막 타이머 체험(3단계)이다")
    void movesToLastStep() {
        OnboardingProgress progress = new OnboardingProgress(true, true);

        assertThat(progress.currentStep()).isEqualTo(3);
        assertThat(progress.isLastStep()).isTrue();
        assertThat(progress.completedSteps()).isEqualTo(2);
    }

    @Test
    @DisplayName("계획을 먼저 세웠더라도 목표일이 없으면 1단계로 돌아간다")
    void ddayComesFirst() {
        OnboardingProgress progress = new OnboardingProgress(false, true);

        assertThat(progress.currentStep()).isEqualTo(1);
        assertThat(progress.isLastStep()).isFalse();
    }

    @Test
    @DisplayName("isStep 으로 화면이 어느 단계를 그릴지 판단한다")
    void isStep() {
        OnboardingProgress progress = new OnboardingProgress(true, false);

        assertThat(progress.isStep(1)).isFalse();
        assertThat(progress.isStep(2)).isTrue();
        assertThat(progress.isStep(3)).isFalse();
    }
}
