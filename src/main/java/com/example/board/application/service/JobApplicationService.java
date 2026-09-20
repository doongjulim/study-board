package com.example.board.application.service;

import com.example.board.application.domain.ApplicationStage;
import com.example.board.application.domain.JobApplication;
import com.example.board.application.domain.StageFocus;
import com.example.board.application.dto.JobApplicationForm;
import com.example.board.application.repository.JobApplicationRepository;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.stats.domain.StatsPeriod;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.service.StudyStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationService {

    /** 안내에 끌어올릴 마감 범위 - D-Day 와 같은 눈높이로 둔다 */
    private static final int UPCOMING_WITHIN_DAYS = 14;

    private final JobApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    /** "지금 단계에 맞게 쓰고 있나" 를 답하려면 이번 주 학습량이 필요하다 (읽기 전용 참조) */
    private final StudyStatisticsService statisticsService;

    public List<JobApplication> findMine(Long memberId) {
        return applicationRepository.findMine(memberId);
    }

    /** 곧 마감인 진행 중 지원 - 홈·일간 뷰가 D-Day 옆에 함께 보여 준다 */
    public List<JobApplication> findUpcoming(Long memberId, LocalDate today) {
        return applicationRepository.findUpcoming(memberId, today.plusDays(UPCOMING_WITHIN_DAYS));
    }

    public JobApplication findOwned(Long id, Long memberId) {
        JobApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("지원 기록이 존재하지 않습니다. id=" + id));
        if (!application.isOwnedBy(memberId)) {
            throw new AccessDeniedException("본인의 지원 기록만 수정하거나 삭제할 수 있습니다.");
        }
        return application;
    }

    @Transactional
    public Long create(JobApplicationForm form, Long memberId) {
        JobApplication application = new JobApplication(
                memberRepository.getReferenceById(memberId),
                form.getCompany(), form.getPosition(), form.getStage(),
                form.getDeadline(), form.getMemo());
        return applicationRepository.save(application).getId();
    }

    @Transactional
    public void update(Long id, JobApplicationForm form, Long memberId) {
        findOwned(id, memberId).update(form.getCompany(), form.getPosition(), form.getStage(),
                form.getResult(), form.getDeadline(), form.getMemo());
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        applicationRepository.delete(findOwned(id, memberId));
    }

    /**
     * 지금 단계와 이번 주 학습량을 나란히 놓는다.
     *
     * <p>― 왜 서비스가 두 모듈을 잇는가<br>
     * 지원 현황(application)과 학습 통계(stats)는 서로를 모른다. 둘을 아는 자리가 하나는 있어야
     * 하는데, 그 자리를 컨트롤러에 두면 화면마다 같은 조합이 다시 쓰인다.
     * 대시보드가 {@code DashboardAssembler} 로 푼 것과 같은 방식이다.
     *
     * <p>진행 중인 지원만 센다. 끝난 지원의 단계는 "지금 무엇을 준비해야 하나" 와 상관이 없다.
     * 결과 대기 단계도 빠진다 - 기다리는 동안 따로 준비할 것이 없기 때문이다.
     */
    public List<StageFocus> currentFocus(Long memberId, LocalDate today) {
        Map<PlanCategory, Integer> counts = countByStudyCategory(findMine(memberId));
        if (counts.isEmpty()) {
            return List.of();
        }

        Map<PlanCategory, Long> studied = weeklyMinutesByCategory(memberId, today);
        List<StageFocus> focus = new ArrayList<>();
        counts.forEach((category, count) ->
                focus.add(new StageFocus(category, count, studied.getOrDefault(category, 0L))));
        // 손대지 않은 것을 먼저, 그다음 지원이 많은 순서 - 목록의 맨 위가 가장 급한 것이어야 한다
        focus.sort((a, b) -> {
            if (a.isNeglected() != b.isNeglected()) {
                return a.isNeglected() ? -1 : 1;
            }
            return Integer.compare(b.applicationCount(), a.applicationCount());
        });
        return List.copyOf(focus);
    }

    private Map<PlanCategory, Integer> countByStudyCategory(List<JobApplication> applications) {
        Map<PlanCategory, Integer> counts = new EnumMap<>(PlanCategory.class);
        for (JobApplication application : applications) {
            PlanCategory category = application.studyCategory();
            if (category != null) {
                counts.merge(category, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<PlanCategory, Long> weeklyMinutesByCategory(Long memberId, LocalDate today) {
        StudyStatistics statistics = statisticsService.calculate(memberId, StatsPeriod.week(today));
        Map<PlanCategory, Long> minutes = new EnumMap<>(PlanCategory.class);
        statistics.categories().forEach(stat -> minutes.put(stat.category(), stat.actualMinutes()));
        return minutes;
    }

    /** 폼의 선택지 - 화면마다 손으로 적지 않게 (ApplicationStage 가 순서까지 정한다) */
    public ApplicationStage[] stages() {
        return ApplicationStage.values();
    }
}
