package com.example.board.plan.domain;

import java.time.LocalDate;

/**
 * 플랜 검색 조건.
 *
 * <p>사용자가 넘긴 값을 그대로 쓰지 않고 한 번 다듬는다.
 * 공백만 있는 검색어, 시작이 끝보다 늦은 기간처럼 결과가 이상해지는 입력을
 * 조회 계층이 아니라 여기서 정리해 두면, 쿼리는 조건이 있는지만 보면 된다.</p>
 *
 * <p>반복 일정이 한 번에 180건까지 생기므로 거르는 수단 없이는 목록에서 찾기 어렵다.</p>
 */
public record PlanSearchCondition(String keyword, PlanCategory category, PlanStatus status,
                                  LocalDate from, LocalDate to) {

    public static PlanSearchCondition of(String keyword, PlanCategory category, PlanStatus status,
                                         LocalDate from, LocalDate to) {
        LocalDate start = from;
        LocalDate end = to;
        // 기간을 거꾸로 넣었으면 빈 결과 대신 뒤집어 준다 (실수를 결과로 벌하지 않는다)
        if (start != null && end != null && start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        return new PlanSearchCondition(
                normalize(keyword),
                category,
                status != null ? status : PlanStatus.ALL,
                start,
                end);
    }

    public boolean hasKeyword() {
        return keyword != null;
    }

    public boolean hasCategory() {
        return category != null;
    }

    /** 조건이 하나도 없는 상태 - 화면에서 안내 문구를 고르는 데 쓴다 */
    public boolean isEmpty() {
        return !hasKeyword() && !hasCategory() && status == PlanStatus.ALL && from == null && to == null;
    }

    /** 완료 여부 조건. 전체면 조건을 걸지 않는다 */
    public Boolean completedFilter() {
        return status.toCompletedFilter();
    }

    private static String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
