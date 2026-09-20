package com.example.board.group.event;

/**
 * 누군가 같은 그룹 사람을 응원했다.
 *
 * <p>group 모듈이 notification 모듈을 직접 부르지 않기 위한 경계다 -
 * 다른 모듈이 이미 같은 방식으로 나뉘어 있다({@code PlanSharedEvent}, {@code WeeklyReportReadyEvent}).</p>
 */
public record CheerSentEvent(Long recipientId, String senderNickname, String groupName, Long groupId) {
}
