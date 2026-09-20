package com.example.board.application.domain;

import com.example.board.common.time.ReadableDuration;
import com.example.board.plan.domain.PlanCategory;

/**
 * "지금 내 전형에 맞게 시간을 쓰고 있나" 에 답하는 한 줄.
 *
 * <p>― 왜 필요한가<br>
 * 통계에는 분류별 학습량이 있었지만 그건 <b>그냥 숫자였다.</b> "코딩테스트 12시간" 이 많은지
 * 적은지 견줄 기준이 없었기 때문이다. 지원 현황이 기준을 만든다:
 * 코테 단계인 회사가 셋인데 이번 주 코딩테스트 학습이 0시간이면, 그것은 숫자가 아니라 경고다.
 *
 * <p>― 왜 "부족하다" 고 말하지 않는가<br>
 * 얼마가 충분한지는 사람마다, 회사마다 다르다. 이 값이 하는 일은 판단을 대신하는 것이 아니라
 * <b>나란히 놓는 것</b>이다 - 지원 3건과 0시간이 한 줄에 있으면 판단은 사용자가 한다.
 * 다만 0시간만은 따로 표시한다({@link #isNeglected}) - 그건 견줄 것도 없는 상태다.
 *
 * @param category        이 단계에서 하게 되는 공부
 * @param applicationCount 지금 그 단계에 있는 진행 중 지원 수
 * @param studiedMinutes   이번 주 그 분류로 실제 공부한 시간(분)
 */
public record StageFocus(PlanCategory category, int applicationCount, long studiedMinutes) {

    /** 지원은 있는데 이번 주 그 공부를 한 번도 하지 않은 상태 */
    public boolean isNeglected() {
        return applicationCount > 0 && studiedMinutes == 0;
    }

    /** 표기 규칙은 {@link ReadableDuration} 하나가 정한다 */
    public String readableStudied() {
        return ReadableDuration.of(studiedMinutes);
    }
}
