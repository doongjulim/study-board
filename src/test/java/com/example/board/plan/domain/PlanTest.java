package com.example.board.plan.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class PlanTest {

    private Member author() {
        return new Member("tester1", "encoded-password", "동주");
    }

    private Plan plan() {
        return new Plan("자료구조 공부", "스택, 큐 복습", author(), PlanCategory.CODING_TEST,
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
    @DisplayName("분류를 지정하지 않으면 기타(ETC)로 저장된다")
    void create_defaultCategory() {
        Plan plan = new Plan("제목", null, author(), null,
                LocalDate.of(2026, 7, 9), null, null);

        assertThat(plan.getCategory()).isEqualTo(PlanCategory.ETC);
    }

    @Test
    @DisplayName("update 로 분류를 바꿀 수 있다")
    void update_category() {
        Plan plan = plan();

        plan.update("면접 준비", null, PlanCategory.INTERVIEW,
                LocalDate.of(2026, 7, 9), null, null);

        assertThat(plan.getCategory()).isEqualTo(PlanCategory.INTERVIEW);
    }

    @Test
    @DisplayName("시작/종료 시간이 없는 종일 일정도 생성할 수 있다")
    void create_allDay() {
        Plan plan = new Plan("휴식", null, author(), PlanCategory.ETC, LocalDate.of(2026, 7, 9), null, null);

        assertThat(plan.getStartTime()).isNull();
        assertThat(plan.getEndTime()).isNull();
    }

    @Test
    @DisplayName("종료 시간이 시작 시간보다 빠르면 생성할 수 없다")
    void create_invalidTimeRange() {
        assertThatThrownBy(() -> new Plan("t", null, author(), PlanCategory.ETC,
                LocalDate.of(2026, 7, 9), LocalTime.of(12, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update 로 내용이 변경된다")
    void update() {
        Plan plan = plan();

        plan.update("알고리즘 공부", "DFS/BFS", PlanCategory.MAJOR, LocalDate.of(2026, 7, 10),
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

        assertThatThrownBy(() -> plan.update("t", null, PlanCategory.ETC, LocalDate.of(2026, 7, 9),
                LocalTime.of(12, 0), LocalTime.of(10, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("update 하면 리마인더가 다시 발송될 수 있도록 초기화된다")
    void update_resetsReminder() {
        Plan plan = plan();
        plan.markReminderSent();

        plan.update("t", null, PlanCategory.ETC, LocalDate.of(2026, 7, 10), LocalTime.of(9, 0), null);

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
    @DisplayName("changeShareScope 는 비공개였다가 공유될 때만 true 를 반환한다 (알림 발행 조건)")
    void changeShareScope_reportsOnlyNewlyShared() {
        Plan plan = plan();

        assertThat(plan.changeShareScope(ShareScope.GROUP)).isTrue();
        assertThat(plan.isShared()).isTrue();

        // 이미 공유된 플랜의 범위 조정은 "새로 공유" 가 아니다 - 같은 사람들에게 또 알리면 소음이다
        assertThat(plan.changeShareScope(ShareScope.PUBLIC)).isFalse();

        assertThat(plan.changeShareScope(ShareScope.PRIVATE)).isFalse();
        assertThat(plan.isShared()).isFalse();
    }

    @Test
    @DisplayName("비공개로 되돌렸다 다시 공유해도 두 번째 알림은 없다 - 껐다 켜기로 알림을 찍어낼 수 없어야 한다")
    void changeShareScope_announcesOnlyOnce() {
        Plan plan = plan();

        assertThat(plan.changeShareScope(ShareScope.PUBLIC)).isTrue();
        plan.changeShareScope(ShareScope.PRIVATE);

        // 전체 공개 알림은 회원 수만큼 퍼진다. 이 줄이 false 가 아니면
        // 버튼을 껐다 켜는 것만으로 알림을 얼마든지 찍어낼 수 있다
        assertThat(plan.changeShareScope(ShareScope.PUBLIC)).isFalse();
        assertThat(plan.isShared()).as("알리지 않을 뿐, 공유 자체는 되어야 한다").isTrue();
    }

    @Test
    @DisplayName("공유 범위를 비워 보내면 비공개로 다룬다 - 잘못된 요청이 실수로 공개로 이어지지 않게")
    void changeShareScope_nullMeansPrivate() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.PUBLIC);

        plan.changeShareScope(null);

        assertThat(plan.getShareScope()).isEqualTo(ShareScope.PRIVATE);
    }

    @Test
    @DisplayName("moveTo 는 날짜만 옮기고 리마인더를 다시 받을 수 있게 한다")
    void moveTo() {
        Plan plan = plan();
        plan.markReminderSent();

        plan.moveTo(LocalDate.of(2026, 7, 10));

        assertThat(plan.getPlanDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(plan.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(plan.isReminderSent()).isFalse();
    }

    @Test
    @DisplayName("moveTo 하면 반복 묶음에서 빠진다 (한 건만 옮긴 것이므로)")
    void moveTo_leavesSeries() {
        Plan plan = plan();
        plan.assignSeries("series-1");

        plan.moveTo(LocalDate.of(2026, 7, 10));

        assertThat(plan.isPartOfSeries()).isFalse();
    }

    @Test
    @DisplayName("이미 완료한 일정은 옮길 수 없다 (지난 기록이 바뀌면 통계가 흔들린다)")
    void moveTo_rejectsCompleted() {
        Plan plan = plan();
        plan.toggleCompleted();

        assertThatThrownBy(() -> plan.moveTo(LocalDate.of(2026, 7, 10)))
                .isInstanceOf(IllegalStateException.class);
    }
}
