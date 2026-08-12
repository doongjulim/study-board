package com.example.board.plan.dto;

import com.example.board.plan.domain.DailyProgress;

/** 완료 토글 후 화면의 "총 N개 / M개 완료" 표시를 갱신하기 위한 값 */
public record DailyProgressResponse(int totalCount, int completedCount,
                                    int remainingCount, int completionRate, boolean allDone) {

    public static DailyProgressResponse from(DailyProgress progress) {
        return new DailyProgressResponse(progress.totalCount(), progress.completedCount(),
                progress.remainingCount(), progress.completionRate(), progress.allDone());
    }
}
