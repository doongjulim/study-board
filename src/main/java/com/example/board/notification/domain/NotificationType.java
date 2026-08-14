package com.example.board.notification.domain;

import com.example.board.member.domain.NotificationPreference;

/**
 * 알림 종류. 회원이 종류별로 끄고 켤 수 있다.
 *
 * <p>설정값 자체는 회원이 들고 있고({@link NotificationPreference}),
 * 종류와 설정 항목을 잇는 일은 알림을 보내는 이쪽에서 한다.
 * 그래야 member 모듈이 알림 종류를 몰라도 된다.</p>
 */
public enum NotificationType {

    /** 일정 시작 전 리마인더 */
    REMINDER,

    /** 다른 회원이 플랜을 공유했을 때 */
    PLAN_SHARED,

    /** 내 글·플랜에 댓글이 달렸을 때 */
    COMMENT;

    public boolean allowedBy(NotificationPreference preference) {
        return switch (this) {
            case REMINDER -> preference.isReminderEnabled();
            case PLAN_SHARED -> preference.isPlanSharedEnabled();
            case COMMENT -> preference.isCommentEnabled();
        };
    }
}
