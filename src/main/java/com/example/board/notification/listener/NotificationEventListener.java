package com.example.board.notification.listener;

import com.example.board.comment.event.CommentAddedEvent;
import com.example.board.group.event.CheerSentEvent;
import com.example.board.group.service.StudyGroupService;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.service.NotificationService;
import com.example.board.plan.event.PlanReminderEvent;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.domain.ShareScope;
import com.example.board.stats.event.WeeklyReportReadyEvent;
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

    /**
     * 응원. 순위표에서 보내고, 받는 쪽은 그룹 화면으로 간다.
     *
     * <p>문구에 그룹 이름을 넣는 이유는 여러 그룹에 속한 사람에게 "누가" 만큼이나
     * "어디서" 가 궁금하기 때문이다.</p>
     */
    @EventListener
    public void handleCheerSent(CheerSentEvent event) {
        notificationService.notify(
                event.recipientId(), NotificationType.CHEER,
                "%s님이 %s에서 응원을 보냈어요 👏".formatted(event.senderNickname(), event.groupName()),
                "/groups/" + event.groupId());
    }

    /**
     * 주간 요약. 누르면 그 주의 기록으로 채워진 인증글 초안이 열린다.
     *
     * <p>알림이 화면 한 곳을 가리키고 끝나면 "봤다" 로 끝난다. 이 알림의 값은 요약 문구가 아니라
     * <b>그 다음에 할 일로 바로 이어진다</b>는 데 있다 - 그래서 주소가 목록이 아니라 초안이다.</p>
     */
    @EventListener
    public void handleWeeklyReportReady(WeeklyReportReadyEvent event) {
        notificationService.notify(
                event.memberId(), NotificationType.WEEKLY_REPORT,
                event.summary() + " — 회고 쓰러 가기",
                "/posts/new?week=" + event.weekStart());
    }
}
