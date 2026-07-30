package com.example.board.plan.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 내 하루 일정 */
    public List<Plan> findDaily(LocalDate date, Long memberId) {
        return planRepository.findByAuthor_IdAndPlanDateOrderByStartTimeAscIdAsc(memberId, date);
    }

    /** 내 주간 일정 */
    public List<Plan> findWeek(LocalDate anyDayOfWeek, Long memberId) {
        LocalDate weekStart = anyDayOfWeek.with(DayOfWeek.MONDAY);
        return planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                memberId, weekStart, weekStart.plusDays(6));
    }

    /** 내 월간 일정 */
    public List<Plan> findMonth(YearMonth month, Long memberId) {
        return planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                memberId, month.atDay(1), month.atEndOfMonth());
    }

    /** 공유된 플랜은 모든 회원의 것을 보여준다 */
    public Page<Plan> findShared(Pageable pageable) {
        return planRepository.findBySharedTrue(pageable);
    }

    public Plan findById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("플랜이 존재하지 않습니다. id=" + id));
    }

    /** 본인 소유 플랜만 반환한다 (수정 폼 등 소유자 전용 화면에서 사용) */
    public Plan findOwned(Long id, Long memberId) {
        Plan plan = findById(id);
        validateOwner(plan, memberId);
        return plan;
    }

    @Transactional
    public Long create(PlanForm form, Long authorId) {
        Member author = memberRepository.getReferenceById(authorId);
        Plan plan = new Plan(form.getTitle(), form.getContent(), author,
                form.getPlanDate(), form.getStartTime(), form.getEndTime());
        return planRepository.save(plan).getId();
    }

    @Transactional
    public void update(Long id, PlanForm form, Long memberId) {
        findOwned(id, memberId).update(form.getTitle(), form.getContent(),
                form.getPlanDate(), form.getStartTime(), form.getEndTime());
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        planRepository.delete(findOwned(id, memberId));
    }

    @Transactional
    public Plan toggleCompleted(Long id, Long memberId) {
        Plan plan = findOwned(id, memberId);
        plan.toggleCompleted();
        return plan;
    }

    @Transactional
    public Plan toggleShared(Long id, Long memberId) {
        Plan plan = findOwned(id, memberId);
        if (plan.toggleShared()) {
            eventPublisher.publishEvent(new PlanSharedEvent(
                    plan.getId(), plan.getAuthor().getId(), plan.getAuthor().getNickname(), plan.getTitle()));
        }
        return plan;
    }

    private void validateOwner(Plan plan, Long memberId) {
        if (!plan.isAuthoredBy(memberId)) {
            throw new AccessDeniedException("본인의 플랜만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
