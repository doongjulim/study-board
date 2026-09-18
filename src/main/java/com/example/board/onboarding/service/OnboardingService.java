package com.example.board.onboarding.service;

import com.example.board.dday.service.DdayService;
import com.example.board.member.service.MemberService;
import com.example.board.onboarding.domain.OnboardingProgress;
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
        return progress(memberId, today, false);
    }

    /**
     * @param goalSkipped 목표일 단계를 건너뛰겠다고 했는가. 목표일은 없을 수도 있는 것이라
     *                    저장된 상태만으로는 "안 적은 사람" 과 "안 적기로 한 사람" 을 가를 수 없다
     */
    public OnboardingProgress progress(Long memberId, LocalDate today, boolean goalSkipped) {
        return new OnboardingProgress(
                !planService.findDaily(today, memberId).isEmpty(),
                !ddayService.findMine(memberId).isEmpty(),
                goalSkipped);
    }

    @Transactional
    public void complete(Long memberId) {
        memberService.completeOnboarding(memberId);
    }
}
