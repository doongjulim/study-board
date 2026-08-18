package com.example.board.stats.domain;

/**
 * 한 사람의 기간 학습 시간과 목표. 순위·챌린지 계산의 입력 단위다.
 *
 * <p>엔티티가 아니라 값으로 받는 이유는, 순위 계산이 DB 없이 검증되어야 하기 때문이다.</p>
 *
 * @param goalMinutes 하루 목표 학습 시간(분). 0 이면 스스로 목표를 끈 사람이다
 */
public record MemberStudyTime(Long memberId, String nickname, long minutes, int goalMinutes) {

    /** 목표를 둔 사람인지 - 0 은 목표 없음이라 달성 판정의 대상이 아니다 */
    public boolean hasGoal() {
        return goalMinutes > 0;
    }

    /** 기간 목표를 채웠는가. 하루 목표에 기간의 날수를 곱해 견준다 */
    public boolean metGoalOver(int days) {
        return hasGoal() && minutes >= (long) goalMinutes * days;
    }
}
