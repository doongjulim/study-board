package com.example.board.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원의 알림 설정.
 *
 * <p>알림을 언제·무엇을 받을지는 회원이 가진 취향이므로 {@link Member} 에 함께 둔다.
 * 다만 member 모듈이 notification 모듈을 알게 되면 안 되므로,
 * "어떤 종류를 허용하는가" 는 알림 종류를 인자로 받지 않고 항목별 메서드로 노출한다.
 * 종류와 항목을 잇는 일은 알림을 보내는 쪽(notification 모듈)이 한다.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    /** 일정 시작 몇 분 전에 알릴지 */
    public static final int DEFAULT_LEAD_MINUTES = 10;

    /**
     * 리드타임 상한. 스케줄러가 한 번에 훑는 구간이기도 하므로,
     * 늘리면 매 분 조회하는 범위가 함께 넓어진다.
     */
    public static final int MAX_LEAD_MINUTES = 60;

    @Column(nullable = false)
    private boolean reminderEnabled = true;

    @Column(nullable = false)
    private int reminderLeadMinutes = DEFAULT_LEAD_MINUTES;

    @Column(nullable = false)
    private boolean planSharedEnabled = true;

    @Column(nullable = false)
    private boolean commentEnabled = true;

    public static NotificationPreference createDefault() {
        return new NotificationPreference();
    }

    /**
     * 지금이 이 일정의 알림 시점을 지났는가.
     * 리마인더를 꺼 두었으면 언제든 false 다.
     */
    public boolean remindsAt(LocalDateTime now, LocalDateTime startAt) {
        if (!reminderEnabled) {
            return false;
        }
        return !now.isBefore(startAt.minusMinutes(reminderLeadMinutes));
    }

    public NotificationPreference change(boolean reminderEnabled, int reminderLeadMinutes,
                                         boolean planSharedEnabled, boolean commentEnabled) {
        if (reminderLeadMinutes < 0 || reminderLeadMinutes > MAX_LEAD_MINUTES) {
            throw new IllegalArgumentException(
                    "알림 시점은 0분 이상 %d분 이하여야 합니다.".formatted(MAX_LEAD_MINUTES));
        }
        return new NotificationPreference(reminderEnabled, reminderLeadMinutes,
                planSharedEnabled, commentEnabled);
    }

    private NotificationPreference(boolean reminderEnabled, int reminderLeadMinutes,
                                   boolean planSharedEnabled, boolean commentEnabled) {
        this.reminderEnabled = reminderEnabled;
        this.reminderLeadMinutes = reminderLeadMinutes;
        this.planSharedEnabled = planSharedEnabled;
        this.commentEnabled = commentEnabled;
    }
}
