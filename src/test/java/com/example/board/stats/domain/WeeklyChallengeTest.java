package com.example.board.stats.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("주간 챌린지")
class WeeklyChallengeTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 10);

    private MemberStudyTime time(long id, String nickname, long minutes, int goal) {
        return new MemberStudyTime(id, nickname, minutes, goal, false);
    }

    @Test
    @DisplayName("그룹 전체 학습 시간을 합산한다")
    void sumsTotalMinutes() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 300, 30),
                time(2L, "나", 200, 30)), WEEK_START);

        assertThat(challenge.totalMinutes()).isEqualTo(500);
        assertThat(challenge.readableTotal()).isEqualTo("8시간 20분");
    }

    @Test
    @DisplayName("각자의 하루 목표를 주간으로 환산해 달성 인원을 센다")
    void countsAchieversByPersonalGoal() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 210, 30),   // 30 × 7 = 210 → 달성
                time(2L, "나", 700, 120),  // 120 × 7 = 840 → 미달
                time(3L, "다", 900, 120)), WEEK_START);

        assertThat(challenge.achievedCount()).isEqualTo(2);
        assertThat(challenge.challengerCount()).isEqualTo(3);
        assertThat(challenge.achievementRate()).isEqualTo(67);
    }

    @Test
    @DisplayName("목표를 끈 사람은 분모에서도 빠진다 - 남의 달성률을 깎지 않는다")
    void membersWithoutGoalExcludedFromRate() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 210, 30),
                time(2L, "나", 0, 0)), WEEK_START);

        assertThat(challenge.challengerCount()).isEqualTo(1);
        assertThat(challenge.memberCount()).isEqualTo(2);
        assertThat(challenge.achievementRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("아무도 목표를 두지 않았으면 달성률은 0 이다 (0으로 나누지 않는다)")
    void noChallengersMeansZeroRate() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 500, 0),
                time(2L, "나", 300, 0)), WEEK_START);

        assertThat(challenge.achievementRate()).isZero();
    }

    @Test
    @DisplayName("1인당 평균은 목표 여부와 무관하게 전체 인원으로 나눈다")
    void averageUsesAllMembers() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 300, 30),
                time(2L, "나", 100, 0)), WEEK_START);

        assertThat(challenge.averageMinutes()).isEqualTo(200);
    }

    @Test
    @DisplayName("기록이 하나도 없는 주도 계산은 된다")
    void emptyWeek() {
        WeeklyChallenge challenge = WeeklyChallenge.of(List.of(
                time(1L, "가", 0, 30)), WEEK_START);

        assertThat(challenge.totalMinutes()).isZero();
        assertThat(challenge.achievedCount()).isZero();
        assertThat(challenge.achievementRate()).isZero();
        assertThat(challenge.readableTotal()).isEqualTo("0분");
    }
}
