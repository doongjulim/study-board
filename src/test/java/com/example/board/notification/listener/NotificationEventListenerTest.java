package com.example.board.notification.listener;

import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.group.service.StudyGroupService;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.service.NotificationService;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.domain.ShareScope;
import com.example.board.plan.event.PlanSharedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock NotificationService notificationService;
    @Mock StudyGroupService studyGroupService;

    @InjectMocks NotificationEventListener listener;

    @Test
    @DisplayName("전체 공개로 공유하면 본인을 제외한 모든 회원에게 알린다")
    void handlePlanShared_public() {
        listener.handlePlanShared(new PlanSharedEvent(1L, 7L, "동주", "면접 준비", ShareScope.PUBLIC));

        then(notificationService).should()
                .notifyAllExcept(eq(7L), contains("면접 준비"), eq("/plans/shared"));
        then(studyGroupService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("그룹 공개로 공유하면 같은 그룹 사람에게만 알린다 - 전체에 뿌리면 그게 스팸이다")
    void handlePlanShared_group() {
        given(studyGroupService.findFellowMemberIds(7L)).willReturn(List.of(7L, 8L, 9L));

        listener.handlePlanShared(new PlanSharedEvent(1L, 7L, "동주", "면접 준비", ShareScope.GROUP));

        then(notificationService).should()
                .notifyMembersExcept(eq(List.of(7L, 8L, 9L)), eq(7L), contains("면접 준비"),
                        eq("/plans/shared"));
        then(notificationService).should(never()).notifyAllExcept(any(), any(), any());
    }

    @Test
    @DisplayName("댓글 이벤트를 받으면 대상 글 작성자에게 알림을 만든다")
    void handleCommentAdded() {
        listener.handleCommentAdded(new CommentAddedEvent(7L, "댓글러", "면접 후기", "/posts/3"));

        then(notificationService).should()
                .notify(eq(7L), eq(NotificationType.COMMENT), contains("면접 후기"), eq("/posts/3"));
    }

    @Test
    @DisplayName("리마인더 이벤트를 받으면 일정 작성자에게만 알림을 만든다")
    void handlePlanReminder() {
        listener.handlePlanReminder(new PlanReminderEvent(1L, 7L, "영어 스터디", LocalTime.of(10, 5)));

        then(notificationService).should()
                .notify(eq(7L), eq(NotificationType.REMINDER), contains("영어 스터디"), eq("/plans/daily"));
    }
}
