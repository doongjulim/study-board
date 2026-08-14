package com.example.board.onboarding.service;

import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
import com.example.board.member.service.MemberService;
import com.example.board.onboarding.domain.OnboardingProgress;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 첫 사용 안내의 조합기.
 *
 * <p>대시보드의 {@code DashboardAssembler} 와 같은 자리다. 온보딩 한 화면 때문에
 * 컨트롤러가 dday·plan·member 를 모두 알게 되지 않도록 조합 책임을 여기서 진다.
 * 각 모듈의 기존 API 를 그대로 쓰므로 온보딩 전용 예외 경로가 생기지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final DdayService ddayService;
    private final PlanService planService;
    private final MemberService memberService;

    /** 어디까지 왔는지 - 도중에 나갔다 돌아와도 하던 곳에서 이어지도록 매번 상태에서 계산한다 */
    public OnboardingProgress progress(Long memberId, LocalDate today) {
        return new OnboardingProgress(
                !ddayService.findMine(memberId).isEmpty(),
                !planService.findDaily(today, memberId).isEmpty());
    }

    @Transactional
    public void createFirstDday(Long memberId, DdayForm form) {
        ddayService.create(form, memberId);
    }

    @Transactional
    public void createFirstPlan(Long memberId, PlanForm form) {
        planService.create(form, memberId);
    }

    @Transactional
    public void complete(Long memberId) {
        memberService.completeOnboarding(memberId);
    }
}
