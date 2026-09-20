package com.example.board.stats.event;

import java.time.LocalDate;

/**
 * 한 주가 끝났고, 그 주에 남길 기록이 있다.
 *
 * <p>― 왜 이벤트인가<br>
 * 통계 모듈이 알림 모듈을 직접 부르면, 통계를 고칠 때마다 알림이 함께 흔들린다.
 * 다른 모듈이 이미 같은 방식으로 나뉘어 있다({@code PlanReminderEvent}, {@code PlanSharedEvent}) -
 * 통계는 "이런 일이 있었다" 까지만 말하고, 그것을 알림으로 만들지는 알림 모듈이 정한다.
 *
 * @param memberId  이 주의 주인
 * @param weekStart 그 주의 월요일. 인증글 초안 주소({@code /posts/new?week=}) 가 이 값을 쓴다
 * @param summary   "이번 주 12시간, 완료율 68%" - 문구를 만드는 것은 숫자를 아는 쪽의 일이다
 */
public record WeeklyReportReadyEvent(Long memberId, LocalDate weekStart, String summary) {
}
