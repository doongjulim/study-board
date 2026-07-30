package com.example.board.plan.event;

import java.time.LocalTime;

/** 일정 시작이 임박했을 때 발행 - notification 모듈이 구독한다 */
public record PlanReminderEvent(Long planId, Long authorId, String title, LocalTime startTime) {
}
