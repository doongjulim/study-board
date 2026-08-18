package com.example.board.calendar.service;

import com.example.board.calendar.domain.PlanCsv;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.member.service.MemberService;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarFeedServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);

    @Mock PlanRepository planRepository;
    @Mock MemberRepository memberRepository;
    @Mock MemberService memberService;

    /** Clock 은 목이 아니라 고정값이라 서비스를 직접 조립한다 (@InjectMocks 는 목만 넣는다) */
    private CalendarFeedService calendarFeedService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        calendarFeedService = new CalendarFeedService(
                planRepository, memberRepository, memberService, clock);
    }

    private Member member(Long id, String nickname) {
        Member owner = new Member("tester" + id, "encoded-password", nickname);
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }

    private Plan plan(Long id, Member author, String title) {
        Plan plan = new Plan(title, null, author, PlanCategory.CODING_TEST,
                TODAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    @Test
    @DisplayName("구독 주소를 발급하면 회원에게 토큰이 붙는다")
    void issueTokenAttachesToMember() {
        Member owner = member(1L, "동주");
        given(memberService.findActive(1L)).willReturn(owner);

        String token = calendarFeedService.issueToken(1L);

        assertThat(token).isNotBlank();
        assertThat(owner.getCalendarToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("다시 발급하면 이전 주소는 그 즉시 무효가 된다")
    void reissueReplacesPreviousToken() {
        Member owner = member(1L, "동주");
        given(memberService.findActive(1L)).willReturn(owner);

        String first = calendarFeedService.issueToken(1L);
        String second = calendarFeedService.issueToken(1L);

        assertThat(second).isNotEqualTo(first);
        assertThat(owner.getCalendarToken()).isEqualTo(second);
    }

    @Test
    @DisplayName("구독을 끊으면 토큰이 사라진다")
    void revokeClearsToken() {
        Member owner = member(1L, "동주");
        owner.issueCalendarToken("old-token");
        given(memberService.findActive(1L)).willReturn(owner);

        calendarFeedService.revokeToken(1L);

        assertThat(owner.hasCalendarToken()).isFalse();
    }

    @Test
    @DisplayName("토큰으로 그 사람의 계획을 달력으로 내보낸다")
    void rendersFeedForTokenOwner() {
        Member owner = member(1L, "동주");
        given(memberRepository.findByCalendarToken("good-token")).willReturn(Optional.of(owner));
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                eq(1L), any(), any())).willReturn(List.of(plan(5L, owner, "알고리즘")));

        String ical = calendarFeedService.renderFeed("good-token");

        assertThat(ical).contains("BEGIN:VCALENDAR", "UID:plan-5@study-board", "SUMMARY:알고리즘");
        assertThat(ical).contains("X-WR-CALNAME:동주님의 학습 플랜");
    }

    @Test
    @DisplayName("구독은 지난 30일부터 앞으로 1년까지만 내보낸다 - 전 기간은 응답이 계속 커진다")
    void feedIsBoundedInTime() {
        Member owner = member(1L, "동주");
        given(memberRepository.findByCalendarToken("good-token")).willReturn(Optional.of(owner));
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                any(), any(), any())).willReturn(List.of());

        calendarFeedService.renderFeed("good-token");

        then(planRepository).should()
                .findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                        1L, TODAY.minusDays(30), TODAY.plusDays(365));
    }

    @Test
    @DisplayName("모르는 토큰은 거절한다")
    void unknownTokenIsRejected() {
        given(memberRepository.findByCalendarToken("bad-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> calendarFeedService.renderFeed("bad-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("탈퇴한 회원의 주소로는 아무것도 나가지 않는다 - 이미 배포된 주소가 남아 있을 수 있다")
    void withdrawnMemberFeedIsRejected() {
        Member owner = member(1L, "동주");
        owner.withdraw(TODAY.atStartOfDay());
        given(memberRepository.findByCalendarToken("stale-token")).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> calendarFeedService.renderFeed("stale-token"))
                .isInstanceOf(IllegalArgumentException.class);
        then(planRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CSV 는 고른 기간의 본인 계획만 담는다")
    void csvUsesRequestedRange() {
        Member owner = member(1L, "동주");
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, TODAY.minusDays(7), TODAY)).willReturn(List.of(plan(5L, owner, "알고리즘")));

        String csv = calendarFeedService.renderCsv(1L, TODAY.minusDays(7), TODAY);

        assertThat(csv).startsWith(PlanCsv.BOM).contains("\"알고리즘\"");
    }
}
