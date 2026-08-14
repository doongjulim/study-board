package com.example.board.onboarding.domain;

/**
 * 첫 사용 안내의 진행 상태.
 *
 * <p>가입 직후 빈 화면만 보면 무엇부터 해야 할지 알 수 없다.
 * 목표를 정하고(1) 오늘 할 일을 적고(2) 타이머를 눌러 보는(3) 세 걸음까지 데려간다.</p>
 *
 * <p>단계는 "무엇을 이미 했는가" 로만 결정되는 순수 값 객체라 DB 없이 검증한다.
 * 도중에 나갔다 돌아와도 하던 곳에서 이어진다.</p>
 */
public record OnboardingProgress(boolean ddayCreated, boolean planCreated) {

    public static final int TOTAL_STEPS = 3;

    /** 지금 보여 줄 단계 (1: 목표일, 2: 오늘 계획, 3: 타이머 체험) */
    public int currentStep() {
        if (!ddayCreated) {
            return 1;
        }
        if (!planCreated) {
            return 2;
        }
        return TOTAL_STEPS;
    }

    public boolean isStep(int step) {
        return currentStep() == step;
    }

    /** 앞 단계를 마쳤는지 - 화면의 체크 표시에 쓴다 */
    public int completedSteps() {
        int completed = 0;
        if (ddayCreated) {
            completed++;
        }
        if (planCreated) {
            completed++;
        }
        return completed;
    }

    /** 마지막 단계(타이머 체험)까지 왔는지 */
    public boolean isLastStep() {
        return currentStep() == TOTAL_STEPS;
    }

    public int percent() {
        return completedSteps() * 100 / TOTAL_STEPS;
    }
}
