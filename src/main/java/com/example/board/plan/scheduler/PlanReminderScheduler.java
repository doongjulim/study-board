package com.example.board.plan.scheduler;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
        for (Plan plan : findUpcoming(now, now.plusMinutes(REMINDER_MINUTES))) {
            plan.markReminderSent();
            eventPublisher.publishEvent(new PlanReminderEvent(
                    plan.getId(), plan.getAuthor().getId(), plan.getTitle(), plan.getStartTime()));
        }
    }

    /**
     * [now, until] 구간에 시작하는 일정을 찾는다.
     * 자정을 넘길 때 다음 날 새벽 일정이 누락되지 않도록 날짜별로 나눠 조회한다.
     * (예: 23:55 에 확인하면 오늘 23:55~24:00 과 내일 00:00~00:05 를 모두 본다)
     */
    private List<Plan> findUpcoming(LocalDateTime now, LocalDateTime until) {
        if (now.toLocalDate().equals(until.toLocalDate())) {
            return findBetween(now.toLocalDate(), now.toLocalTime(), until.toLocalTime());
        }

        List<Plan> upcoming = new ArrayList<>(
                findBetween(now.toLocalDate(), now.toLocalTime(), LocalTime.MAX));
        upcoming.addAll(findBetween(until.toLocalDate(), LocalTime.MIN, until.toLocalTime()));
        return upcoming;
    }

    private List<Plan> findBetween(LocalDate date, LocalTime from, LocalTime to) {
        return planRepository.findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
                date, from, to);
    }
}
