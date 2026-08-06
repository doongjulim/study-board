package com.example.board.plan.domain;

/** 취업 준비 학습 분류 - 통계에서 "무엇에 시간을 썼는지" 집계하는 기준이 된다 */
public enum PlanCategory {

    CODING_TEST("코딩테스트"),
    RESUME("자소서"),
    INTERVIEW("면접"),
    CERTIFICATE("자격증"),
    MAJOR("전공"),
    LANGUAGE("어학"),
    ETC("기타");

    private final String label;

    PlanCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
