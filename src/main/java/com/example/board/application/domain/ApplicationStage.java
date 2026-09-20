package com.example.board.application.domain;

import com.example.board.plan.domain.PlanCategory;

/**
 * 지원 전형의 단계.
 *
 * <p>― 왜 단계마다 학습 분류를 매다는가<br>
 * 이 앱에는 이미 분류별 학습량 통계가 있다. 그런데 그건 <b>그냥 숫자였다</b> -
 * "코딩테스트 12시간" 이 많은 것인지 적은 것인지 견줄 기준이 없었다.
 * 지원 현황이 붙으면 기준이 생긴다: 코테 단계에 들어간 회사가 셋인데 이번 주 코딩테스트 학습이
 * 0시간이면, 그건 숫자가 아니라 <b>경고</b>다. 숫자에 기준이 생기면 판단이 된다.
 *
 * <p>― 왜 단계를 enum 으로 고정하는가<br>
 * 회사마다 전형 이름이 다르지만(1차/실무진/기술면접), 준비하는 일은 몇 갈래로 모인다.
 * 자유 입력으로 두면 같은 단계가 열 가지 이름으로 쌓여 통계와 이을 수 없게 된다 -
 * 날짜 포맷이 열두 가지로 갈라졌던 것과 같은 일이다.
 * 여기 없는 이름은 메모에 적으면 된다.
 *
 * <p>순서가 곧 진행 순서다. 화면의 진행 표시와 다음 단계 제안이 이 순서를 쓴다.
 */
public enum ApplicationStage {

    DOCUMENT("서류", PlanCategory.RESUME),
    CODING_TEST("코딩테스트", PlanCategory.CODING_TEST),
    INTERVIEW_FIRST("1차 면접", PlanCategory.INTERVIEW),
    INTERVIEW_FINAL("최종 면접", PlanCategory.INTERVIEW),
    RESULT("결과 대기", null);

    private final String label;
    /** 이 단계에서 주로 하게 되는 공부. 결과 대기에는 준비할 것이 없으므로 null 이다 */
    private final PlanCategory studyCategory;

    ApplicationStage(String label, PlanCategory studyCategory) {
        this.label = label;
        this.studyCategory = studyCategory;
    }

    public String getLabel() {
        return label;
    }

    public PlanCategory getStudyCategory() {
        return studyCategory;
    }

    public boolean hasStudyCategory() {
        return studyCategory != null;
    }

    /** 몇 번째 단계인가 (1부터) - 화면의 진행 표시가 쓴다 */
    public int step() {
        return ordinal() + 1;
    }

    public static int totalSteps() {
        return values().length;
    }
}
