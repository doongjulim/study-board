package com.example.board.plan.repository;

import com.example.board.plan.domain.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면에 노출되는 조회는
 * {@link EntityGraph} 로 author 를 함께 로딩한다.
 */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Plan> findById(Long id);

    /** 회원의 하루 일정 (종일 일정이 먼저, 이후 시작 시간순) */
    @EntityGraph(attributePaths = "author")
    List<Plan> findByAuthor_IdAndPlanDateOrderByStartTimeAscIdAsc(Long authorId, LocalDate planDate);

    /** 회원의 기간 일정 - 주간/월간 뷰에서 사용 */
    @EntityGraph(attributePaths = "author")
    List<Plan> findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
            Long authorId, LocalDate start, LocalDate end);

    /** 공유된 플랜 목록 */
    @EntityGraph(attributePaths = "author")
    Page<Plan> findBySharedTrue(Pageable pageable);

    /** 같은 반복 묶음의 일정 전체 - 반복 일정 일괄 삭제에 사용 */
    @EntityGraph(attributePaths = "author")
    List<Plan> findBySeriesId(String seriesId);

    /** 리마인더 대상: 해당 날짜 일정 중 아직 완료/발송되지 않았고 곧 시작하는 것 (알림 발송에 author 필요) */
    @EntityGraph(attributePaths = "author")
    List<Plan> findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
            LocalDate planDate, LocalTime from, LocalTime to);
}
