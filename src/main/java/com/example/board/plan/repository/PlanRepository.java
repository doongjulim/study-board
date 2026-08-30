package com.example.board.plan.repository;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.ShareScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면에 노출되는 조회는
 * {@link EntityGraph} 로 author 를 함께 로딩한다.
 */
public interface PlanRepository extends JpaRepository<Plan, Long>, JpaSpecificationExecutor<Plan> {

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Plan> findById(Long id);

    /*
     * ── 정렬에 nulls first 를 직접 적는 이유 ────────────────────────────
     *
     * startTime 은 nullable 이다(종일 일정). 그런데 "NULL 이 어디로 가는가" 는 SQL 표준이
     * 정해 두지 않아 DB 마다 다르다 - H2 는 NULL 을 가장 작은 값으로 보아 맨 위에 두고,
     * PostgreSQL 은 가장 큰 값으로 보아 맨 아래에 둔다.
     *
     * 메서드 이름만으로 만든 쿼리(OrderByStartTimeAsc)는 그 차이를 그대로 물려받는다.
     * 그래서 H2 로 개발하는 동안에는 종일 일정이 목록 맨 위에 있다가, 운영 DB 를 PostgreSQL 로
     * 바꾸는 순간 아무도 건드리지 않은 화면의 순서가 뒤집힌다. 마이그레이션은 벤더별로 갈라 두면서
     * 정렬만 DB 에 맡겨 두면, 이식성은 반쪽이다.
     *
     * 그래서 이름은 그대로 두되(호출부가 읽는 의미는 같다) 쿼리를 직접 적어 위치를 못 박는다.
     * "종일 일정이 먼저" 는 우리가 고른 규칙이지 DB 가 정해 줄 일이 아니다.
     * (PlanRepositoryOrderTest 가 이 규칙을 지킨다)
     */

    /** 회원의 하루 일정 (종일 일정이 먼저, 이후 시작 시간순) */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Plan p
            where p.author.id = :authorId and p.planDate = :planDate
            order by p.startTime asc nulls first, p.id asc
            """)
    List<Plan> findByAuthor_IdAndPlanDateOrderByStartTimeAscIdAsc(
            @Param("authorId") Long authorId, @Param("planDate") LocalDate planDate);

    /** 회원의 기간 일정 - 주간/월간 뷰에서 사용 */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Plan p
            where p.author.id = :authorId and p.planDate between :start and :end
            order by p.planDate asc, p.startTime asc nulls first, p.id asc
            """)
    List<Plan> findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
            @Param("authorId") Long authorId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** 공유 플랜 목록 (그룹이 없는 회원용) - 전체 공개만 보인다 */
    @EntityGraph(attributePaths = "author")
    Page<Plan> findByShareScope(ShareScope shareScope, Pageable pageable);

    /**
     * 공유 플랜 목록 - 전체 공개에 더해, 같은 그룹 사람들의 그룹 공개까지 보인다.
     * 빈 컬렉션은 in () 구문 오류가 되므로 그룹이 없으면 {@link #findByShareScope} 를 쓴다.
     */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Plan p
            where p.shareScope = com.example.board.plan.domain.ShareScope.PUBLIC
               or (p.shareScope = com.example.board.plan.domain.ShareScope.GROUP
                   and p.author.id in :fellowIds)
            """)
    Page<Plan> findSharedVisibleTo(@Param("fellowIds") List<Long> fellowIds, Pageable pageable);

    /** 같은 반복 묶음의 일정 전체 - 반복 일정 일괄 삭제에 사용 */
    @EntityGraph(attributePaths = "author")
    List<Plan> findBySeriesId(String seriesId);

    /** 그날 남은 일정 - 다음 날로 이월할 대상 (정렬 규칙은 위 설명과 같다) */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Plan p
            where p.author.id = :authorId and p.planDate = :planDate and p.completed = false
            order by p.startTime asc nulls first, p.id asc
            """)
    List<Plan> findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(
            @Param("authorId") Long authorId, @Param("planDate") LocalDate planDate);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByAuthor_Id(Long authorId);

    /** 리마인더 대상: 해당 날짜 일정 중 아직 완료/발송되지 않았고 곧 시작하는 것 (알림 발송에 author 필요) */
    @EntityGraph(attributePaths = "author")
    List<Plan> findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
            LocalDate planDate, LocalTime from, LocalTime to);
}
