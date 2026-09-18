package com.example.board.stats.domain;

import com.example.board.common.time.ReadableDuration;

/**
 * 한 사람의 기간 학습 시간과 목표. 순위·챌린지 계산의 입력 단위다.
 *
 * <p>엔티티가 아니라 값으로 받는 이유는, 순위 계산이 DB 없이 검증되어야 하기 때문이다.</p>
 *
 * @param goalMinutes     하루 목표 학습 시간(분). 0 이면 스스로 목표를 끈 사람이다
 * @param hasProfileImage 순위표에 얼굴을 함께 그릴지. 없는 사람에게 이미지 요청을 보내지 않기 위해 함께 든다
 */
public record MemberStudyTime(Long memberId, String nickname, long minutes, int goalMinutes,
                              boolean hasProfileImage) {

    /** 목표를 둔 사람인지 - 0 은 목표 없음이라 달성 판정의 대상이 아니다 */
    public boolean hasGoal() {
        return goalMinutes > 0;
    }

    /** 기간 목표를 채웠는가. 하루 목표에 기간의 날수를 곱해 견준다 */
    public boolean metGoalOver(int days) {
        return hasGoal() && minutes >= (long) goalMinutes * days;
    }

    /**
     * 분 합계를 "3시간 20분" 처럼 읽히게 한다. 패키지 안에서 공용.
     *
     * <p>규칙 자체는 {@link ReadableDuration} 한 곳에 있다 - 플래너의 예상 소요 시간도 같은 표기를
     * 써야 해서 공용으로 옮겼다. 이 메서드는 통계 쪽 호출부를 위한 이름만 남긴 것이다.</p>
     */
    static String readableMinutes(long minutes) {
        return ReadableDuration.of(minutes);
    }
}
