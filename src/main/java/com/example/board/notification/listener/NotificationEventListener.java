package com.example.board.notification.listener;

import com.example.board.notification.service.NotificationService;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.event.PlanSharedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/** plan 모듈이 발행한 이벤트를 알림으로 변환한다 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;

    @EventListener
    public void handlePlanShared(PlanSharedEvent event) {
        notificationService.notify(
                event.nickname() + "님이 플랜을 공유했습니다: " + event.title(),
                "/plans/shared");
    }

    @EventListener
    public void handlePlanReminder(PlanReminderEvent event) {
        notificationService.notify(
                "곧 시작하는 일정이 있어요: " + event.title() + " (" + event.startTime().format(TIME_FORMATTER) + ")",
                "/plans/daily");
    }
}
