package com.example.board.plan.domain;

/** 검색에서 쓰는 완료 여부 필터 */
public enum PlanStatus {

    ALL("전체"),
    TODO("남은 것"),
    DONE("끝낸 것");

    private final String label;

    PlanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 조회에 넘길 완료 여부. 전체를 볼 때는 조건을 걸지 않으므로 null */
    public Boolean toCompletedFilter() {
        return switch (this) {
            case ALL -> null;
            case TODO -> Boolean.FALSE;
            case DONE -> Boolean.TRUE;
        };
    }
}
