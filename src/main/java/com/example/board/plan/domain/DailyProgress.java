package com.example.board.plan.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 하루치 계획 진행 상황.
 *
 * <p>완료 토글이 화면 새로고침 없이 끝나려면 서버가 "그래서 오늘 몇 개 중 몇 개가 끝났는지" 를
 * 함께 돌려줘야 한다. 계획 목록만으로 계산되는 순수 값 객체라 DB 없이 검증한다.</p>
 */
public record DailyProgress(int totalCount, int completedCount) {

    public static DailyProgress of(List<Plan> plans) {
        return new DailyProgress(plans.size(),
                (int) plans.stream().filter(Plan::isCompleted).count());
    }

    @JsonProperty
    public int remainingCount() {
        return totalCount - completedCount;
    }

    @JsonProperty
    public int completionRate() {
        return totalCount == 0 ? 0 : (int) Math.round(completedCount * 100.0 / totalCount);
    }

    /** 계획을 세웠고 남김없이 끝낸 상태 */
    @JsonProperty
    public boolean allDone() {
        return totalCount > 0 && completedCount == totalCount;
    }
}
