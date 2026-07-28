package com.example.board.plan.service;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.repository.PlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock PlanRepository planRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PlanService planService;

    private PlanForm form() {
        PlanForm form = new PlanForm();
        form.setTitle("자료구조 공부");
        form.setContent("스택, 큐 복습");
        form.setWriter("동주");
        form.setPlanDate(LocalDate.of(2026, 7, 9));
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(12, 0));
        return form;
    }

    private Plan plan() {
        return new Plan("자료구조 공부", "스택, 큐 복습", "동주",
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    // ── create / update / delete ─────────────────────────────

    @Test
    @DisplayName("create - 폼 값으로 플랜을 저장하고 id 를 반환한다")
    void create() {
        given(planRepository.save(any(Plan.class))).willAnswer(inv -> {
            Plan saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        Long id = planService.create(form());

        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        then(planRepository).should().save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("자료구조 공부");
        assertThat(captor.getValue().getPlanDate()).isEqualTo(LocalDate.of(2026, 7, 9));
    }

    @Test
    @DisplayName("update - 폼 값으로 플랜을 수정한다")
    void update() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        PlanForm form = form();
        form.setTitle("알고리즘 공부");
        planService.update(1L, form);

        assertThat(plan.getTitle()).isEqualTo("알고리즘 공부");
    }

    @Test
    @DisplayName("delete - 플랜을 삭제한다")
    void delete() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.delete(1L);

        then(planRepository).should().delete(plan);
    }

    @Test
    @DisplayName("findById - 없으면 IllegalArgumentException 이 발생한다")
    void findById_notFound() {
        given(planRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> planService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── 조회 범위 ─────────────────────────────────────────────

    @Test
    @DisplayName("findWeek - 어떤 요일을 넘겨도 월~일 범위로 조회한다")
    void findWeek() {
        LocalDate wednesday = LocalDate.of(2026, 7, 8);
        given(planRepository.findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)))
                .willReturn(List.of());

        planService.findWeek(wednesday);

        then(planRepository).should().findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));
    }

    @Test
    @DisplayName("findMonth - 해당 월의 1일부터 말일까지 조회한다")
    void findMonth() {
        given(planRepository.findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .willReturn(List.of());

        planService.findMonth(YearMonth.of(2026, 7));

        then(planRepository).should().findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    // ── 상태 변경 ─────────────────────────────────────────────

    @Test
    @DisplayName("toggleCompleted - 완료 상태가 반전된다")
    void toggleCompleted() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.toggleCompleted(1L);

        assertThat(plan.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("toggleShared - 공유로 전환되면 PlanSharedEvent 를 발행한다")
    void toggleShared_publishesEvent() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.toggleShared(1L);

        assertThat(plan.isShared()).isTrue();
        ArgumentCaptor<PlanSharedEvent> captor = ArgumentCaptor.forClass(PlanSharedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("자료구조 공부");
    }

    @Test
    @DisplayName("toggleShared - 공유 해제 시에는 이벤트를 발행하지 않는다")
    void toggleShared_offDoesNotPublish() {
        Plan plan = plan();
        plan.toggleShared(); // 이미 공유 상태
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.toggleShared(1L);

        assertThat(plan.isShared()).isFalse();
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
