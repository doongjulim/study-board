package com.example.board.plan.scheduler;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PlanReminderSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 9);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Mock PlanRepository planRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PlanReminderScheduler scheduler;

    private Member author() {
        Member author = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(author, "id", 7L);
        return author;
    }

    private Plan plan(String title, LocalDate date, LocalTime startTime) {
        return new Plan(title, null, author(), PlanCategory.LANGUAGE, date, startTime, null);
    }

    @Test
    @DisplayName("10분 내에 시작하는 플랜에 리마인더 이벤트를 발행하고 재발송을 막는다")
    void sendReminder() {
        Plan plan = plan("영어 스터디", TODAY, LocalTime.of(10, 5));
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TODAY, LocalTime.of(10, 0), LocalTime.of(10, 10)))
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
    @DisplayName("자정을 넘기는 구간은 오늘 남은 시간과 다음 날 새벽을 모두 조회한다")
    void acrossMidnight_queriesBothDays() {
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                any(), any(), any())).willReturn(List.of());

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 23, 55));

        then(planRepository).should().findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TODAY, LocalTime.of(23, 55), LocalTime.MAX);
        then(planRepository).should().findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TOMORROW, LocalTime.MIN, LocalTime.of(0, 5));
    }

    @Test
    @DisplayName("자정 직후에 시작하는 다음 날 일정도 리마인더를 받는다")
    void acrossMidnight_sendsReminderForNextDayPlan() {
        Plan earlyMorningPlan = plan("새벽 코딩테스트", TOMORROW, LocalTime.of(0, 5));
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TODAY, LocalTime.of(23, 55), LocalTime.MAX)).willReturn(List.of());
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TOMORROW, LocalTime.MIN, LocalTime.of(0, 5))).willReturn(List.of(earlyMorningPlan));

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 23, 55));

        assertThat(earlyMorningPlan.isReminderSent()).isTrue();
        ArgumentCaptor<PlanReminderEvent> captor = ArgumentCaptor.forClass(PlanReminderEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("새벽 코딩테스트");
    }

    @Test
    @DisplayName("자정을 넘기면 양쪽 날짜의 일정을 모두 발송한다")
    void acrossMidnight_sendsBothDays() {
        Plan tonightPlan = plan("자기 전 복습", TODAY, LocalTime.of(23, 58));
        Plan earlyMorningPlan = plan("새벽 코딩테스트", TOMORROW, LocalTime.of(0, 3));
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TODAY, LocalTime.of(23, 55), LocalTime.MAX)).willReturn(List.of(tonightPlan));
        given(planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                TOMORROW, LocalTime.MIN, LocalTime.of(0, 5))).willReturn(List.of(earlyMorningPlan));

        scheduler.sendUpcomingPlanReminders(LocalDateTime.of(2026, 7, 9, 23, 55));

        then(eventPublisher).should(times(2)).publishEvent(any(PlanReminderEvent.class));
        assertThat(tonightPlan.isReminderSent()).isTrue();
        assertThat(earlyMorningPlan.isReminderSent()).isTrue();
    }
}
