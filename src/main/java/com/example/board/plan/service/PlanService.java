package com.example.board.plan.service;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ApplicationEventPublisher eventPublisher;

    public List<Plan> findDaily(LocalDate date) {
        return planRepository.findByPlanDateOrderByStartTimeAscIdAsc(date);
    }

    public List<Plan> findWeek(LocalDate anyDayOfWeek) {
        LocalDate weekStart = anyDayOfWeek.with(DayOfWeek.MONDAY);
        return planRepository.findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                weekStart, weekStart.plusDays(6));
    }

    public List<Plan> findMonth(YearMonth month) {
        return planRepository.findByPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                month.atDay(1), month.atEndOfMonth());
    }

    public Page<Plan> findShared(Pageable pageable) {
        return planRepository.findBySharedTrue(pageable);
    }

    public Plan findById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("플랜이 존재하지 않습니다. id=" + id));
    }

    @Transactional
    public Long create(PlanForm form) {
        Plan plan = new Plan(form.getTitle(), form.getContent(), form.getWriter(),
                form.getPlanDate(), form.getStartTime(), form.getEndTime());
        return planRepository.save(plan).getId();
    }

    @Transactional
    public void update(Long id, PlanForm form) {
        findById(id).update(form.getTitle(), form.getContent(),
                form.getPlanDate(), form.getStartTime(), form.getEndTime());
    }

    @Transactional
    public void delete(Long id) {
        planRepository.delete(findById(id));
    }

    @Transactional
    public Plan toggleCompleted(Long id) {
        Plan plan = findById(id);
        plan.toggleCompleted();
        return plan;
    }

    @Transactional
    public Plan toggleShared(Long id) {
        Plan plan = findById(id);
        if (plan.toggleShared()) {
            eventPublisher.publishEvent(new PlanSharedEvent(plan.getId(), plan.getWriter(), plan.getTitle()));
        }
        return plan;
    }
}
