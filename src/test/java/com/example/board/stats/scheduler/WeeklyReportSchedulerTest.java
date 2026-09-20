package com.example.board.stats.scheduler;

import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import com.example.board.stats.domain.StatsPeriod;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.event.WeeklyReportReadyEvent;
import com.example.board.stats.service.StudyStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WeeklyReportSchedulerTest {

    /** 2026-09-20 은 일요일. 그 주는 9/14(월)~9/20(일) 이다 */
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 20);
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Mock MemberService memberService;
    @Mock StudyStatisticsService statisticsService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks WeeklyReportScheduler scheduler;

    private final Member owner = new Member("tester1", "encoded-password", "동주");

    private Plan plan(boolean completed) {
        Plan plan = new Plan("공부", null, owner, PlanCategory.MAJOR, MONDAY, null, null, 60);
        if (completed) {
            plan.toggleCompleted();
        }
        return plan;
    }

    private StudySession session(int minutes) {
        LocalDateTime start = MONDAY.atTime(LocalTime.of(9, 0));
        StudySession session = StudySession.start(owner, null, PlanCategory.MAJOR, start);
        session.stop(start.plusMinutes(minutes));
        return session;
    }

    private void givenStatistics(Long memberId, List<Plan> plans, List<StudySession> sessions) {
        given(statisticsService.calculate(eq(memberId), any(StatsPeriod.class)))
                .willReturn(StudyStatistics.of(plans, sessions, MONDAY, SUNDAY));
    }

    private WeeklyReportReadyEvent published() {
        ArgumentCaptor<WeeklyReportReadyEvent> captor =
                ArgumentCaptor.forClass(WeeklyReportReadyEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("그 주에 기록이 있는 회원에게 요약과 그 주의 월요일을 함께 알린다")
    void publishesSummary() {
        given(memberService.findIdsAllowingWeeklyReportNotification()).willReturn(List.of(7L));
        givenStatistics(7L, List.of(plan(true), plan(false)), List.of(session(180)));

        scheduler.sendWeeklyReports(SUNDAY);

        WeeklyReportReadyEvent event = published();
        assertThat(event.memberId()).isEqualTo(7L);
        // 인증글 초안 주소(/posts/new?week=)가 이 값을 쓴다
        assertThat(event.weekStart()).isEqualTo(MONDAY);
        assertThat(event.summary()).isEqualTo("이번 주 3시간, 완료율 50%");
    }

    @Test
    @DisplayName("아무 기록도 없는 주에는 보내지 않는다 - '이번 주 0시간' 은 요약이 아니라 질책이다")
    void skipsEmptyWeek() {
        given(memberService.findIdsAllowingWeeklyReportNotification()).willReturn(List.of(7L));
        givenStatistics(7L, List.of(), List.of());

        scheduler.sendWeeklyReports(SUNDAY);

        then(eventPublisher).should(never()).publishEvent(any(WeeklyReportReadyEvent.class));
    }

    @Test
    @DisplayName("계획 없이 타이머만 쓴 주도 보낸다 - 완료율 대신 공부한 시간만 적는다")
    void sessionsOnly() {
        given(memberService.findIdsAllowingWeeklyReportNotification()).willReturn(List.of(7L));
        givenStatistics(7L, List.of(), List.of(session(45)));

        scheduler.sendWeeklyReports(SUNDAY);

        // 계획이 0개면 완료율은 0% 인데, 그걸 적으면 열심히 한 주가 실패한 주로 보인다
        assertThat(published().summary()).isEqualTo("이번 주 45분 공부했어요");
    }

    @Test
    @DisplayName("일요일 어느 날짜를 넣어도 그 주 월~일을 본다")
    void usesThisWeek() {
        given(memberService.findIdsAllowingWeeklyReportNotification()).willReturn(List.of(7L));
        givenStatistics(7L, List.of(plan(true)), List.of(session(60)));

        scheduler.sendWeeklyReports(SUNDAY);

        ArgumentCaptor<StatsPeriod> captor = ArgumentCaptor.forClass(StatsPeriod.class);
        then(statisticsService).should().calculate(eq(7L), captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(MONDAY);
        assertThat(captor.getValue().to()).isEqualTo(SUNDAY);
    }

    @Test
    @DisplayName("설정을 꺼 둔 회원은 조회 단계에서 빠진다 - 사람 수만큼 통계를 계산하지 않는다")
    void skipsOptedOutMembers() {
        given(memberService.findIdsAllowingWeeklyReportNotification()).willReturn(List.of());

        scheduler.sendWeeklyReports(SUNDAY);

        then(statisticsService).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
