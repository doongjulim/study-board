package com.example.board.plan.scheduler;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.repository.PlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PlanReminderSchedulerTest {

    @Mock PlanRepository planRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PlanReminderScheduler scheduler;

    @Test
    @DisplayName("10분 내에 시작하는 플랜에 리마인더 이벤트를 발행하고 재발송을 막는다")
    void sendReminder() {
        com.example.board.member.domain.Member author =
                new com.example.board.member.domain.Member("tester1", "encoded-password", "동주");
        org.springframework.test.util.ReflectionTestUtils.setField(author, "id", 7L);
        Plan plan = new Plan("영어 스터디", null, author,
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 5), null);
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(10, 10)))
                .willReturn(List.of(plan));

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 10, 0));

        assertThat(plan.isReminderSent()).isTrue();
        ArgumentCaptor<PlanReminderEvent> captor = ArgumentCaptor.forClass(PlanReminderEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("영어 스터디");
        assertThat(captor.getValue().authorId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("대상 플랜이 없으면 이벤트를 발행하지 않는다")
    void noTarget() {
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                any(), any(), any())).willReturn(List.of());

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 10, 0));

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("자정 직전에는 당일 자정까지만 조회한다 (날짜가 넘어가지 않는다)")
    void nearMidnight() {
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                any(), any(), any())).willReturn(List.of());

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 23, 55));

        then(planRepository).should().findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                LocalDate.of(2026, 7, 9), LocalTime.of(23, 55), LocalTime.MAX);
    }
}
