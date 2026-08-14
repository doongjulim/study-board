package com.example.board.plan.scheduler;

import com.example.board.member.domain.NotificationPreference;
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

/**
 * 곧 시작하는 일정을 1분마다 확인해 리마인더 이벤트를 발행한다.
 *
 * <p>알림 시점은 회원마다 다르므로(마이페이지에서 0~60분), 한 번에 훑는 구간은
 * 가장 이른 시점인 {@code MAX_LEAD_MINUTES} 로 잡고 실제로 보낼지는 회원 설정으로 판단한다.
 * 구간을 회원별로 나눠 조회하면 매 분 회원 수만큼 쿼리가 나가기 때문이다.</p>
 */
@Component
@RequiredArgsConstructor
public class PlanReminderScheduler {

    /** 조회 구간 - 회원이 고를 수 있는 가장 이른 알림 시점과 같다 */
    static final int MAX_LEAD_MINUTES = NotificationPreference.MAX_LEAD_MINUTES;

    private final PlanRepository planRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void sendUpcomingPlanReminders() {
        sendUpcomingPlanReminders(LocalDateTime.now());
    }

    void sendUpcomingPlanReminders(LocalDateTime now) {
        for (Plan plan : findUpcoming(now, now.plusMinutes(MAX_LEAD_MINUTES))) {
            NotificationPreference preference = plan.getAuthor().getNotificationPreference();
            // 아직 그 회원의 알림 시점이 아니면 다음 분에 다시 본다 (발송됨으로 표시하지 않는다)
            if (!preference.remindsAt(now, plan.startsAt())) {
                continue;
            }
            plan.markReminderSent();
            eventPublisher.publishEvent(new PlanReminderEvent(
                    plan.getId(), plan.getAuthor().getId(), plan.getTitle(), plan.getStartTime()));
        }
    }

    /**
     * [now, until] 구간에 시작하는 일정을 찾는다.
     * 자정을 넘길 때 다음 날 새벽 일정이 누락되지 않도록 날짜별로 나눠 조회한다.
     * (예: 23:30 에 확인하면 오늘 23:30~24:00 과 내일 00:00~00:30 을 모두 본다)
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
