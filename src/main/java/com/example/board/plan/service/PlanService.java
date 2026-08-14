package com.example.board.plan.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanSearchCondition;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.repository.PlanSpecifications;
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
import java.util.UUID;

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

    /**
     * 일정을 등록한다. 반복 설정이 있으면 종료일까지의 일정을 한 번에 만들고
     * 같은 seriesId 로 묶어, 이후 한꺼번에 삭제할 수 있게 한다.
     * 반환값은 첫 일정의 id.
     */
    @Transactional
    public Long create(PlanForm form, Long authorId) {
        Member author = memberRepository.getReferenceById(authorId);
        List<LocalDate> dates = form.getRepeatType().datesBetween(form.getPlanDate(), form.getRepeatUntil());
        String seriesId = (dates.size() > 1) ? UUID.randomUUID().toString() : null;

        List<Plan> plans = dates.stream()
                .map(date -> {
                    Plan plan = new Plan(form.getTitle(), form.getContent(), author, form.getCategory(),
                            date, form.getStartTime(), form.getEndTime());
                    if (seriesId != null) {
                        plan.assignSeries(seriesId);
                    }
                    return plan;
                })
                .toList();
        return planRepository.saveAll(plans).get(0).getId();
    }

    @Transactional
    public void update(Long id, PlanForm form, Long memberId) {
        findOwned(id, memberId).update(form.getTitle(), form.getContent(), form.getCategory(),
                form.getPlanDate(), form.getStartTime(), form.getEndTime());
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        planRepository.delete(findOwned(id, memberId));
    }

    /** 반복 일정 전체 삭제 - 본인 소유의 같은 묶음만 지운다. 삭제한 건수를 반환한다 */
    @Transactional
    public int deleteSeries(Long id, Long memberId) {
        Plan plan = findOwned(id, memberId);
        if (!plan.isPartOfSeries()) {
            planRepository.delete(plan);
            return 1;
        }
        List<Plan> series = planRepository.findBySeriesId(plan.getSeriesId()).stream()
                .filter(target -> target.isAuthoredBy(memberId))
                .toList();
        planRepository.deleteAll(series);
        return series.size();
    }

    /**
     * 내 계획 검색. 반복 일정이 한 번에 180건까지 생기므로 거르는 수단이 필요하다.
     * 소유 조건을 항상 먼저 걸어 남의 계획이 섞이지 않게 한다.
     */
    public Page<Plan> search(Long memberId, PlanSearchCondition condition, Pageable pageable) {
        return planRepository.findAll(
                PlanSpecifications.ownedBy(memberId).and(PlanSpecifications.matching(condition)),
                pageable);
    }

    /** 그날 남은 일정 - 이월 안내 배너에 쓴다 */
    public List<Plan> findUnfinished(LocalDate date, Long memberId) {
        return planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(
                memberId, date);
    }

    /**
     * 못 끝낸 일정을 다른 날로 옮긴다. 옮긴 건수를 반환한다.
     * 계획을 다시 적게 하지 않는 것이, 미룬 일을 없던 일로 만들지 않는 가장 쉬운 방법이다.
     */
    @Transactional
    public int rollover(Long memberId, LocalDate from, LocalDate to) {
        List<Plan> unfinished = findUnfinished(from, memberId);
        unfinished.forEach(plan -> plan.moveTo(to));
        return unfinished.size();
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
