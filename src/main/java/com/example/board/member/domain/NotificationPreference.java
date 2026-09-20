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

    /**
     * 일요일 저녁의 주간 리포트 알림.
     *
     * <p>기본이 켜짐인 이유는 주 1회이고, 받는 사람이 <b>그 주에 실제로 공부한 사람</b>뿐이기 때문이다.
     * 기록이 없는 주에는 보내지 않으므로 "아무것도 안 했는데 알림만 오는" 상황이 생기지 않는다.</p>
     */
    @Column(nullable = false)
    private boolean weeklyReportEnabled = true;

    /**
     * 같은 그룹 사람이 보낸 응원.
     *
     * <p>기본이 켜짐인 이유는 받는 쪽에 손해가 없는 알림이고, 하루 한 번으로 묶여 있어
     * 한 사람이 아무리 눌러도 하루 한 통을 넘지 않기 때문이다({@code Cheer}).</p>
     */
    @Column(nullable = false)
    private boolean cheerEnabled = true;

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

    /**
     * 알림 설정을 바꾼 새 값을 돌려준다.
     *
     * <p>종류별 켜짐/꺼짐을 {@link NotificationToggles} 로 받는 이유는 그쪽에 적어 두었다 -
     * 한마디로, 종류가 늘 때마다 인자가 늘어나는 구조였고 부르는 자리가 모두 따라 바뀌었다.</p>
     *
     * <p>리드타임만 따로 받는 것은 그것이 켜짐/꺼짐이 아니라 <b>수치</b>이고,
     * 검사가 필요한 유일한 값이기 때문이다.</p>
     */
    public NotificationPreference change(int reminderLeadMinutes, NotificationToggles toggles) {
        if (reminderLeadMinutes < 0 || reminderLeadMinutes > MAX_LEAD_MINUTES) {
            throw new IllegalArgumentException(
                    "알림 시점은 0분 이상 %d분 이하여야 합니다.".formatted(MAX_LEAD_MINUTES));
        }
        return new NotificationPreference(reminderLeadMinutes, toggles);
    }

    private NotificationPreference(int reminderLeadMinutes, NotificationToggles toggles) {
        this.reminderLeadMinutes = reminderLeadMinutes;
        this.reminderEnabled = toggles.reminder();
        this.planSharedEnabled = toggles.planShared();
        this.commentEnabled = toggles.comment();
        this.weeklyReportEnabled = toggles.weeklyReport();
        this.cheerEnabled = toggles.cheer();
    }
}
