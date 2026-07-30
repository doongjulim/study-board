package com.example.board.notification.listener;

import com.example.board.notification.service.NotificationService;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.event.PlanSharedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock NotificationService notificationService;

    @InjectMocks NotificationEventListener listener;

    @Test
    @DisplayName("플랜 공유 이벤트를 받으면 공유한 본인을 제외한 전체에게 알림을 만든다")
    void handlePlanShared() {
        listener.handlePlanShared(new PlanSharedEvent(1L, 7L, "동주", "면접 준비"));

        then(notificationService).should()
                .notifyAllExcept(eq(7L), contains("면접 준비"), eq("/plans/shared"));
    }

    @Test
    @DisplayName("리마인더 이벤트를 받으면 일정 작성자에게만 알림을 만든다")
    void handlePlanReminder() {
        listener.handlePlanReminder(new PlanReminderEvent(1L, 7L, "영어 스터디", LocalTime.of(10, 5)));

        then(notificationService).should()
                .notify(eq(7L), contains("영어 스터디"), eq("/plans/daily"));
    }
}
