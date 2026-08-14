package com.example.board.plan.repository;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanSearchCondition;
import org.springframework.data.jpa.domain.Specification;

/**
 * 검색 조건을 쿼리로 옮긴다.
 *
 * <p>조건이 있을 때만 where 절이 붙어야 하는데, JPQL 에 {@code :param is null or ...} 를
 * 늘어놓으면 읽기 어렵고 파라미터 타입 문제도 생기기 쉽다.
 * 조건 하나가 메서드 하나로 떨어져 있으면 나중에 조건을 더할 때도 이 파일만 열면 된다.</p>
 */
public final class PlanSpecifications {

    private PlanSpecifications() {
    }

    /** 남의 계획이 섞이지 않도록 항상 먼저 건다 */
    public static Specification<Plan> ownedBy(Long memberId) {
        return (root, query, cb) -> cb.equal(root.get("author").get("id"), memberId);
    }

    public static Specification<Plan> matching(PlanSearchCondition condition) {
        Specification<Plan> spec = (root, query, cb) -> cb.conjunction();

        if (condition.hasKeyword()) {
            spec = spec.and(titleOrContentContains(condition.keyword()));
        }
        if (condition.hasCategory()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), condition.category()));
        }
        if (condition.completedFilter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("completed"), condition.completedFilter()));
        }
        if (condition.from() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("planDate"), condition.from()));
        }
        if (condition.to() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("planDate"), condition.to()));
        }
        return spec;
    }

    /** 제목과 메모를 함께 본다 - 대소문자는 구분하지 않는다 */
    private static Specification<Plan> titleOrContentContains(String keyword) {
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("content"), "")), pattern));
    }
}
