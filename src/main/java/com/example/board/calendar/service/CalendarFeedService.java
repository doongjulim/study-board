package com.example.board.calendar.service;

import com.example.board.auth.TokenHasher;
import com.example.board.calendar.domain.ICalendar;
import com.example.board.calendar.domain.PlanCsv;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.member.service.MemberService;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 계획을 밖으로 내보낸다 - 구독(iCal)과 내려받기(CSV).
 *
 * <p>구글 캘린더를 쓰는 사람에게 "이쪽으로 옮겨 오라" 고 하면 아무도 옮기지 않는다.
 * 쓰던 캘린더에 이 플래너가 얹히게 두는 편이 낫다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarFeedService {

    /**
     * 구독으로 내보내는 기간. 지난 것은 캘린더에 남아 있어도 의미가 없고,
     * 앞으로 1년이면 시험 일정까지 들어간다. 전 기간을 매번 내보내면 응답이 계속 커진다.
     */
    private static final int FEED_PAST_DAYS = 30;
    private static final int FEED_FUTURE_DAYS = 365;

    private final PlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final Clock clock;

    /** 구독 주소를 발급한다. 이미 있으면 새로 뽑아 이전 주소를 무효로 만든다 */
    @Transactional
    public String issueToken(Long memberId) {
        String token = TokenHasher.newToken();
        memberService.findActive(memberId).issueCalendarToken(token);
        return token;
    }

    @Transactional
    public void revokeToken(Long memberId) {
        memberService.findActive(memberId).revokeCalendarToken();
    }

    /**
     * 토큰으로 달력 본문을 만든다.
     *
     * <p>탈퇴한 회원은 걸러야 한다 - 탈퇴 시 토큰을 지우지만, 그 전에 이미 배포된 주소가
     * 남아 있을 수 있으므로 조회 단계에서 한 번 더 막는다.</p>
     */
    public String renderFeed(String token) {
        Member owner = memberRepository.findByCalendarToken(token)
                .filter(member -> !member.isWithdrawn())
                .orElseThrow(() -> new IllegalArgumentException("구독 주소가 올바르지 않습니다."));

        LocalDate today = LocalDate.now(clock);
        List<Plan> plans = findPlans(owner.getId(),
                today.minusDays(FEED_PAST_DAYS), today.plusDays(FEED_FUTURE_DAYS));
        return ICalendar.render(plans, owner.getNickname() + "님의 학습 플랜");
    }

    /** 내려받기는 로그인한 본인 것만 - 기간은 화면에서 고른 대로 준다 */
    public String renderCsv(Long memberId, LocalDate from, LocalDate to) {
        return PlanCsv.render(findPlans(memberId, from, to));
    }

    private List<Plan> findPlans(Long memberId, LocalDate from, LocalDate to) {
        return planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                memberId, from, to);
    }
}
