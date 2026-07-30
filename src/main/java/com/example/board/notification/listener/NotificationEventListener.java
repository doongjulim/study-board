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
        // 공유 소식은 공유한 본인을 제외한 모든 회원에게 알린다
        notificationService.notifyAllExcept(
                event.authorId(),
                event.nickname() + "님이 플랜을 공유했습니다: " + event.title(),
                "/plans/shared");
    }

    @EventListener
    public void handlePlanReminder(PlanReminderEvent event) {
        // 리마인더는 일정 작성자 본인에게만 보낸다
        notificationService.notify(
                event.authorId(),
                "곧 시작하는 일정이 있어요: " + event.title() + " (" + event.startTime().format(TIME_FORMATTER) + ")",
                "/plans/daily");
    }
}
