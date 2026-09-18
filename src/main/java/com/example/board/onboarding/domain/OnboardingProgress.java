package com.example.board.onboarding.domain;

/**
 * 첫 사용 안내의 진행 상태.
 *
 * <p>가입 직후 빈 화면만 보면 무엇부터 해야 할지 알 수 없다.
 * 오늘 할 일을 적고(1) 목표일을 정하고(2) 타이머를 눌러 보는(3) 세 걸음까지 데려간다.</p>
 *
 * <p>― 왜 D-Day 가 첫 걸음이 아닌가<br>
 * 처음에는 <b>"목표가 뭔가요?"</b> 로 시작했다. 그런데 그 화면을 보는 사람은 아직
 * 이 도구가 자기에게 뭘 해 주는지 모른다. 시험일을 적는 것은 <b>이 도구를 쓰기로 한 다음</b>의 일이고,
 * 첫 질문으로 놓이면 "아직 시험일이 안 정해졌는데" 하고 멈추게 된다.
 * 그래서 30초 만에 첫 성공(할 일 하나를 적고 그것이 목록에 나타나는 것)을 먼저 만든다.</p>
 *
 * <p>단계는 "무엇을 이미 했는가" 로만 결정되는 순수 값 객체라 DB 없이 검증한다.
 * 도중에 나갔다 돌아와도 하던 곳에서 이어진다. 예외는 {@code goalSkipped} 하나인데,
 * 목표일은 <b>없을 수도 있는 것</b>이라 건너뛴 사람을 붙잡아 둘 수 없기 때문이다
 * (그 값은 저장하지 않고 주소로 들고 다닌다 - 뒤로 가기가 그대로 동작한다).</p>
 */
public record OnboardingProgress(boolean planCreated, boolean ddayCreated, boolean goalSkipped) {

    public static final int TOTAL_STEPS = 3;

    /** 지금 보여 줄 단계 (1: 오늘 할 일, 2: 목표일, 3: 타이머 체험) */
    public int currentStep() {
        if (!planCreated) {
            return 1;
        }
        if (!ddayCreated && !goalSkipped) {
            return 2;
        }
        return TOTAL_STEPS;
    }

    public boolean isStep(int step) {
        return currentStep() == step;
    }

    /** 앞 단계를 <b>실제로</b> 마쳤는지 - 화면의 체크 표시에 쓴다. 건너뛴 것은 한 것이 아니다 */
    public int completedSteps() {
        return (planCreated ? 1 : 0) + (ddayCreated ? 1 : 0);
    }

    /** 마지막 단계(타이머 체험)까지 왔는지 */
    public boolean isLastStep() {
        return currentStep() == TOTAL_STEPS;
    }

    public int percent() {
        return completedSteps() * 100 / TOTAL_STEPS;
    }
}
