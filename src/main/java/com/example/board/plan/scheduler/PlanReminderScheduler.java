package com.example.board.plan.scheduler;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 곧 시작하는 일정을 1분마다 확인해 리마인더 이벤트를 발행한다 */
@Component
@RequiredArgsConstructor
public class PlanReminderScheduler {

    static final int REMINDER_MINUTES = 10;

    private final PlanRepository planRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void sendUpcomingPlanReminders() {
        sendUpcomingPlanReminders(LocalDateTime.now());
    }

    void sendUpcomingPlanReminders(LocalDateTime now) {
        LocalTime from = now.toLocalTime();
        LocalTime to = from.plusMinutes(REMINDER_MINUTES);
        if (to.isBefore(from)) {
            to = LocalTime.MAX; // 자정을 넘기면 당일 끝까지만 조회
        }

        List<Plan> upcoming = planRepository
                .findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                        now.toLocalDate(), from, to);

        for (Plan plan : upcoming) {
            plan.markReminderSent();
            eventPublisher.publishEvent(new PlanReminderEvent(
                    plan.getId(), plan.getAuthor().getId(), plan.getTitle(), plan.getStartTime()));
        }
    }
}
