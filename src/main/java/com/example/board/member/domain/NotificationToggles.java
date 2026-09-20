package com.example.board.member.domain;

/**
 * 알림 종류별 켜짐/꺼짐.
 *
 * <p>― 왜 묶었는가<br>
 * 설정을 바꾸는 메서드가 {@code change(boolean, int, boolean, boolean)} 이었다.
 * 종류가 하나 늘 때마다 인자가 하나씩 붙었고, 그때마다 <b>부르는 모든 자리</b>를 고쳐야 했다.
 * 게다가 {@code change(true, 10, true, false, true)} 는 읽어도 무엇이 켜진 것인지 알 수 없다 -
 * 순서를 하나 바꿔 적어도 컴파일은 통과하고, 잘못된 것은 알림이 안 오는 것으로만 드러난다.
 *
 * <p>종류가 늘어날 자리이므로(지금도 다섯이다) 늘어나는 비용을 여기 한 곳으로 모은다.
 * 필드명이 곧 알림 종류이고, {@link #allOn()} 과 {@code with*} 로 바꿀 것만 적는다.
 *
 * <p>이 타입이 알림 <b>종류</b>(notification 모듈의 enum)를 알지 않는 것은 의도적이다 -
 * member 모듈은 "무엇을 켜 두었는가" 만 알고, 종류와 항목을 잇는 일은 알림을 보내는 쪽이 한다.
 */
public record NotificationToggles(boolean reminder, boolean planShared, boolean comment,
                                  boolean weeklyReport, boolean cheer) {

    public static NotificationToggles allOn() {
        return new NotificationToggles(true, true, true, true, true);
    }

    public NotificationToggles withReminder(boolean on) {
        return new NotificationToggles(on, planShared, comment, weeklyReport, cheer);
    }

    public NotificationToggles withPlanShared(boolean on) {
        return new NotificationToggles(reminder, on, comment, weeklyReport, cheer);
    }

    public NotificationToggles withComment(boolean on) {
        return new NotificationToggles(reminder, planShared, on, weeklyReport, cheer);
    }

    public NotificationToggles withWeeklyReport(boolean on) {
        return new NotificationToggles(reminder, planShared, comment, on, cheer);
    }

    public NotificationToggles withCheer(boolean on) {
        return new NotificationToggles(reminder, planShared, comment, weeklyReport, on);
    }
}
