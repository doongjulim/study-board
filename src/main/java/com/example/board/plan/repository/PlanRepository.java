package com.example.board.plan.repository;

import com.example.board.plan.domain.Plan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** 하루 일정 (종일 일정이 먼저, 이후 시작 시간순) */
    List<Plan> findByPlanDateOrderByStartTimeAscIdAsc(LocalDate planDate);

    /** 기간 일정 - 주간/월간 뷰에서 사용 */
    List<Plan> findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(LocalDate start, LocalDate end);

    /** 공유된 플랜 목록 */
    Page<Plan> findBySharedTrue(Pageable pageable);

    /** 리마인더 대상: 오늘 일정 중 아직 완료/발송되지 않았고 곧 시작하는 것 */
    List<Plan> findByPlanDateAndCompletedFalseAndReminderSentFalseAndStartTimeBetween(
            LocalDate planDate, LocalTime from, LocalTime to);
}
