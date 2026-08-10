package com.example.board.home.domain;

/**
 * 오늘 목표 대비 진행도. 대시보드의 진행률 링에 쓰인다.
 *
 * <p>실제 학습 시간과 목표 시간만으로 결정되는 순수 값 객체라 DB 없이 검증한다.
 * 링은 100% 를 넘지 않도록 잘라 두어, 목표를 크게 넘겨도 그래프가 깨지지 않는다.</p>
 */
public record GoalProgress(long actualMinutes, int goalMinutes) {

    private static final int FULL = 100;

    public static GoalProgress of(long actualMinutes, int goalMinutes) {
        return new GoalProgress(Math.max(actualMinutes, 0), Math.max(goalMinutes, 0));
    }

    /** 목표를 정해 두었는가 - 0 이면 시간 목표를 쓰지 않는 상태다 */
    public boolean hasGoal() {
        return goalMinutes > 0;
    }

    /**
     * 링을 채울 비율(%). 0~100 으로 잘린다.
     * 목표를 꺼 둔 상태에서는 "공부했으면 채움" 으로 본다.
     */
    public int percent() {
        if (!hasGoal()) {
            return actualMinutes > 0 ? FULL : 0;
        }
        return (int) Math.min(FULL, Math.round(actualMinutes * 100.0 / goalMinutes));
    }

    public boolean reached() {
        return hasGoal() ? actualMinutes >= goalMinutes : actualMinutes > 0;
    }

    /** 목표까지 남은 분. 이미 채웠으면 0 */
    public long remainingMinutes() {
        return Math.max(goalMinutes - actualMinutes, 0);
    }

    public long actualHours() {
        return actualMinutes / 60;
    }

    public long actualRemainderMinutes() {
        return actualMinutes % 60;
    }

    public long goalHours() {
        return goalMinutes / 60;
    }

    public long goalRemainderMinutes() {
        return goalMinutes % 60;
    }
}
