package com.example.board.plan.event;

/** 플랜이 새로 공유되었을 때 발행 - notification 모듈이 구독한다 */
public record PlanSharedEvent(Long planId, String nickname, String title) {
}
