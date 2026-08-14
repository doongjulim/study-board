package com.example.board.notification.listener;

import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.group.service.StudyGroupService;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.service.NotificationService;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.domain.ShareScope;
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
    /** 그룹 공개의 수신 대상을 구하는 데만 쓴다 - 읽기 전용 참조 */
    private final StudyGroupService studyGroupService;

    /**
     * 공유 소식을 알린다. 누구에게 보낼지는 공유 범위가 정한다 -
     * 전체 공개는 모든 회원, 그룹 공개는 같은 그룹 사람만.
     * 그룹에만 공유한 플랜을 전체에 알리면 그게 곧 스팸이고, 범위 설정도 무의미해진다.
     */
    @EventListener
    public void handlePlanShared(PlanSharedEvent event) {
        String message = event.nickname() + "님이 플랜을 공유했습니다: " + event.title();

        if (event.scope() == ShareScope.PUBLIC) {
            notificationService.notifyAllExcept(event.authorId(), message, "/plans/shared");
            return;
        }
        // 같은 그룹 사람들 (작성자 본인 포함 - 발신자 제외는 알림 서비스가 한다)
        notificationService.notifyMembersExcept(
                studyGroupService.findFellowMemberIds(event.authorId()),
                event.authorId(), message, "/plans/shared");
    }

    @EventListener
    public void handleCommentAdded(CommentAddedEvent event) {
        // 댓글 알림은 대상 글/플랜의 작성자에게만 보낸다
        notificationService.notify(
                event.recipientId(), NotificationType.COMMENT,
                event.commenterNickname() + "님이 댓글을 남겼습니다: " + event.targetTitle(),
                event.url());
    }

    @EventListener
    public void handlePlanReminder(PlanReminderEvent event) {
        // 리마인더는 일정 작성자 본인에게만 보낸다
        notificationService.notify(
                event.authorId(), NotificationType.REMINDER,
                "곧 시작하는 일정이 있어요: " + event.title() + " (" + event.startTime().format(TIME_FORMATTER) + ")",
                "/plans/daily");
    }
}
