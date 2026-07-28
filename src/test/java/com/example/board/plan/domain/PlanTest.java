package com.example.board.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class PlanTest {

    private Plan plan() {
        return new Plan("자료구조 공부", "스택, 큐 복습", "동주",
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("생성 시 완료/공유/리마인더 발송 여부는 모두 false 다")
    void create_defaults() {
        Plan plan = plan();

        assertThat(plan.isCompleted()).isFalse();
        assertThat(plan.isShared()).isFalse();
        assertThat(plan.isReminderSent()).isFalse();
    }

    @Test
    @DisplayName("시작/종료 시간이 없는 종일 일정도 생성할 수 있다")
    void create_allDay() {
        Plan plan = new Plan("휴식", null, "동주", LocalDate.of(2026, 7, 9), null, null);

        assertThat(plan.getStartTime()).isNull();
        assertThat(plan.getEndTime()).isNull();
    }

    @Test
    @DisplayName("종료 시간이 시작 시간보다 빠르면 생성할 수 없다")
    void create_invalidTimeRange() {
        assertThatThrownBy(() -> new Plan("t", null, "w",
                LocalDate.of(2026, 7, 9), LocalTime.of(12, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update 로 내용이 변경된다")
    void update() {
        Plan plan = plan();

        plan.update("알고리즘 공부", "DFS/BFS", LocalDate.of(2026, 7, 10),
                LocalTime.of(14, 0), LocalTime.of(16, 0));

        assertThat(plan.getTitle()).isEqualTo("알고리즘 공부");
        assertThat(plan.getContent()).isEqualTo("DFS/BFS");
        assertThat(plan.getPlanDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(plan.getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("update 시 종료 시간이 시작 시간보다 빠르면 예외가 발생한다")
    void update_invalidTimeRange() {
        Plan plan = plan();

        assertThatThrownBy(() -> plan.update("t", null, LocalDate.of(2026, 7, 9),
                LocalTime.of(12, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update 하면 리마인더가 다시 발송될 수 있도록 초기화된다")
    void update_resetsReminder() {
        Plan plan = plan();
        plan.markReminderSent();

        plan.update("t", null, LocalDate.of(2026, 7, 10), LocalTime.of(9, 0), null);

        assertThat(plan.isReminderSent()).isFalse();
    }

    @Test
    @DisplayName("toggleCompleted 는 완료 상태를 반전시킨다")
    void toggleCompleted() {
        Plan plan = plan();

        plan.toggleCompleted();
        assertThat(plan.isCompleted()).isTrue();

        plan.toggleCompleted();
        assertThat(plan.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("toggleShared 는 공유 상태를 반전시키고 새 공유 여부를 반환한다")
    void toggleShared() {
        Plan plan = plan();

        assertThat(plan.toggleShared()).isTrue();
        assertThat(plan.isShared()).isTrue();

        assertThat(plan.toggleShared()).isFalse();
        assertThat(plan.isShared()).isFalse();
    }
}
