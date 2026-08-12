package com.example.board.plan.dto;

import com.example.board.plan.domain.Plan;

import java.time.format.DateTimeFormatter;

/** 일간 목록의 한 줄. 화면을 새로 그리지 않고 이 값만으로 행을 만든다 */
public record PlanRowResponse(Long id, String title, String category, String time,
                              boolean completed, boolean partOfSeries, boolean shared) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static PlanRowResponse from(Plan plan) {
        return new PlanRowResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getCategory().getLabel(),
                formatTime(plan),
                plan.isCompleted(),
                plan.isPartOfSeries(),
                plan.isShared());
    }

    private static String formatTime(Plan plan) {
        if (plan.getStartTime() == null) {
            return "종일";
        }
        if (plan.getEndTime() == null) {
            return plan.getStartTime().format(TIME);
        }
        return plan.getStartTime().format(TIME) + " ~ " + plan.getEndTime().format(TIME);
    }
}
