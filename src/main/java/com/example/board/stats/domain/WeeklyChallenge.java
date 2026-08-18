package com.example.board.stats.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 그룹의 이번 주 공동 목표 현황.
 *
 * <p>그룹장이 따로 목표를 정하게 하지 않는다. 사람마다 이미 하루 목표 시간을 갖고 있으므로,
 * 그것을 주간으로 환산해 "몇 명이 자기 목표를 지켰나" 를 본다.
 * 새 설정 화면과 컬럼을 만들지 않고도 챌린지가 성립하고,
 * 각자에게 맞는 기준이라 하루 30분 하는 사람과 3시간 하는 사람이 같은 잣대로 비교되지 않는다.</p>
 *
 * @param challengerCount 목표를 둔 사람 수. 목표를 0 으로 끈 사람은 달성 판정 대상이 아니므로 분모에서도 뺀다
 */
public record WeeklyChallenge(LocalDate weekStart, long totalMinutes,
                              int achievedCount, int challengerCount, int memberCount) {

    /** 주간 챌린지의 기간 - 월요일부터 7일 */
    public static final int DAYS = 7;

    public static WeeklyChallenge of(List<MemberStudyTime> times, LocalDate weekStart) {
        long total = times.stream().mapToLong(MemberStudyTime::minutes).sum();
        int challengers = (int) times.stream().filter(MemberStudyTime::hasGoal).count();
        int achieved = (int) times.stream().filter(time -> time.metGoalOver(DAYS)).count();
        return new WeeklyChallenge(weekStart, total, achieved, challengers, times.size());
    }

    /** 달성률(%). 목표를 둔 사람이 아무도 없으면 0 - 0으로 나누지 않는다 */
    public int achievementRate() {
        if (challengerCount == 0) {
            return 0;
        }
        return (int) Math.round(achievedCount * 100.0 / challengerCount);
    }

    /** 1인당 평균 학습 시간(분). 빈 그룹은 있을 수 없지만 방어한다 */
    public long averageMinutes() {
        return (memberCount == 0) ? 0 : totalMinutes / memberCount;
    }

    /** 그룹 전체 합계를 "12시간 30분" 처럼 읽히게 */
    public String readableTotal() {
        if (totalMinutes < 60) {
            return totalMinutes + "분";
        }
        long hours = totalMinutes / 60;
        long rest = totalMinutes % 60;
        return (rest == 0) ? hours + "시간" : hours + "시간 " + rest + "분";
    }
}
