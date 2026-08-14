package com.example.board.plan.event;

import com.example.board.plan.domain.ShareScope;

/**
 * 플랜이 새로 공유되었을 때 발행 - notification 모듈이 구독한다.
 * 범위를 함께 실어, 누구에게 알릴지(전체 vs 같은 그룹)를 구독자가 정할 수 있게 한다.
 */
public record PlanSharedEvent(Long planId, Long authorId, String nickname, String title,
                              ShareScope scope) {
}
