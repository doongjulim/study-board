package com.example.board.plan.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DailyProgress")
class DailyProgressTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(boolean completed) {
        Plan plan = new Plan("공부", null, author, PlanCategory.MAJOR, TODAY, null, null);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    @Test
    @DisplayName("계획이 없으면 모든 수치가 0 이다")
    void empty() {
        DailyProgress progress = DailyProgress.of(List.of());

        assertThat(progress.totalCount()).isZero();
        assertThat(progress.completedCount()).isZero();
        assertThat(progress.remainingCount()).isZero();
        assertThat(progress.completionRate()).isZero();
        assertThat(progress.allDone()).isFalse();
    }

    @Test
    @DisplayName("완료 개수와 남은 개수를 센다")
    void counts() {
        DailyProgress progress = DailyProgress.of(List.of(
                plan(true), plan(true), plan(false)));

        assertThat(progress.totalCount()).isEqualTo(3);
        assertThat(progress.completedCount()).isEqualTo(2);
        assertThat(progress.remainingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("완료율은 반올림한다")
    void completionRate() {
        DailyProgress progress = DailyProgress.of(List.of(
                plan(true), plan(false), plan(false)));

        assertThat(progress.completionRate()).isEqualTo(33);
    }

    @Test
    @DisplayName("남김없이 끝낸 날을 구분한다")
    void allDone() {
        assertThat(DailyProgress.of(List.of(plan(true), plan(true))).allDone()).isTrue();
        assertThat(DailyProgress.of(List.of(plan(true), plan(false))).allDone()).isFalse();
    }
}
