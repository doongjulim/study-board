package com.example.board.plan.domain;

import com.example.board.common.time.ReadableDuration;

/**
 * 예상 소요 시간의 기본 선택지.
 *
 * <p>― 왜 상수 목록을 따로 두는가<br>
 * 예상 소요 시간을 숫자 입력으로 받으면 '한 줄 추가' 가 더 이상 한 줄이 아니게 된다.
 * 떠오른 할 일을 적는 자리에서 키보드로 분을 치게 하면, 결국 아무도 적지 않는다 -
 * 계획 시간이 비어 있어 통계가 조용히 사라졌던 원인이 정확히 그것이었다.
 * 클릭 한 번으로 끝나야 채워진다.
 *
 * <p>세 개인 이유는 더 늘리면 고르는 일 자체가 부담이 되기 때문이다.
 * 여기 없는 길이는 '자세히 입력' 에서 직접 적을 수 있다 - 흔한 길은 빠르게, 드문 길은 가능하게.
 *
 * <p>화면마다 손으로 적지 않고 이 목록을 쓰는 이유는 날짜 포맷에서 배운 것과 같다.
 * 흩어 두면 어느 화면은 30·60·120 이 되고 어느 화면은 25·50 이 된다.
 */
public enum EstimatePreset {

    HALF_HOUR(30),
    ONE_HOUR(60),
    TWO_HOURS(120);

    private final int minutes;

    EstimatePreset(int minutes) {
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    /** "1시간" 처럼 읽히는 이름 - 표기 규칙은 {@link ReadableDuration} 하나가 정한다 */
    public String getLabel() {
        return ReadableDuration.of(minutes);
    }
}
