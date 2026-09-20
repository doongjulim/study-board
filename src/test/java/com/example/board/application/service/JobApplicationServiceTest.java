package com.example.board.application.service;

import com.example.board.application.domain.ApplicationResult;
import com.example.board.application.domain.ApplicationStage;
import com.example.board.application.domain.JobApplication;
import com.example.board.application.domain.StageFocus;
import com.example.board.application.repository.JobApplicationRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import com.example.board.stats.domain.StatsPeriod;
import com.example.board.stats.domain.StudyStatistics;
import com.example.board.stats.service.StudyStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("지원 현황")
class JobApplicationServiceTest {

    private static final Long MEMBER_ID = 1L;
    /** 2026-09-17 은 목요일. 그 주는 9/14(월)~9/20(일) */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    @Mock JobApplicationRepository applicationRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyStatisticsService statisticsService;

    @InjectMocks JobApplicationService applicationService;

    private Member owner() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private JobApplication application(String company, ApplicationStage stage, ApplicationResult result) {
        JobApplication application = new JobApplication(owner(), company, null, stage, null, null);
        if (result != ApplicationResult.IN_PROGRESS) {
            application.update(company, null, stage, result, null, null);
        }
        return application;
    }

    private StudySession session(PlanCategory category, int minutes) {
        LocalDateTime start = TODAY.atTime(LocalTime.of(9, 0));
        StudySession studySession = StudySession.start(owner(), null, category, start);
        studySession.stop(start.plusMinutes(minutes));
        return studySession;
    }

    private void givenWeeklyStudy(StudySession... sessions) {
        given(statisticsService.calculate(eq(MEMBER_ID), any(StatsPeriod.class)))
                .willReturn(StudyStatistics.of(List.<Plan>of(), List.of(sessions),
                        LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20)));
    }

    @Test
    @DisplayName("지금 단계와 이번 주 학습량을 나란히 놓는다 - 숫자에 기준이 생기면 판단이 된다")
    void currentFocus() {
        given(applicationRepository.findMine(MEMBER_ID)).willReturn(List.of(
                application("카카오", ApplicationStage.CODING_TEST, ApplicationResult.IN_PROGRESS),
                application("네이버", ApplicationStage.CODING_TEST, ApplicationResult.IN_PROGRESS),
                application("라인", ApplicationStage.INTERVIEW_FIRST, ApplicationResult.IN_PROGRESS)));
        givenWeeklyStudy(session(PlanCategory.CODING_TEST, 120));

        List<StageFocus> focus = applicationService.currentFocus(MEMBER_ID, TODAY);

        assertThat(focus).extracting(StageFocus::category, StageFocus::applicationCount,
                        StageFocus::studiedMinutes)
                .containsExactly(
                        // 손대지 않은 것이 먼저 - 목록의 맨 위가 가장 급한 것이어야 한다
                        org.assertj.core.api.Assertions.tuple(PlanCategory.INTERVIEW, 1, 0L),
                        org.assertj.core.api.Assertions.tuple(PlanCategory.CODING_TEST, 2, 120L));
    }

    @Test
    @DisplayName("끝난 지원은 세지 않는다 - 떨어진 회사의 단계는 지금 할 일과 상관이 없다")
    void ignoresClosedApplications() {
        given(applicationRepository.findMine(MEMBER_ID)).willReturn(List.of(
                application("카카오", ApplicationStage.CODING_TEST, ApplicationResult.FAILED),
                application("네이버", ApplicationStage.INTERVIEW_FIRST, ApplicationResult.IN_PROGRESS)));
        givenWeeklyStudy();

        assertThat(applicationService.currentFocus(MEMBER_ID, TODAY))
                .extracting(StageFocus::category)
                .containsExactly(PlanCategory.INTERVIEW);
    }

    @Test
    @DisplayName("결과 대기 단계는 빠진다 - 기다리는 동안 따로 준비할 것이 없다")
    void ignoresResultStage() {
        given(applicationRepository.findMine(MEMBER_ID)).willReturn(List.of(
                application("카카오", ApplicationStage.RESULT, ApplicationResult.IN_PROGRESS)));
        givenWeeklyStudy();

        assertThat(applicationService.currentFocus(MEMBER_ID, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("지원이 하나도 없으면 통계를 조회하지도 않는다")
    void noApplicationsNoStatsQuery() {
        given(applicationRepository.findMine(MEMBER_ID)).willReturn(List.of());

        assertThat(applicationService.currentFocus(MEMBER_ID, TODAY)).isEmpty();
        org.mockito.BDDMockito.then(statisticsService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("1차·최종 면접은 같은 분류로 합쳐진다 - 준비하는 일이 같다")
    void interviewStagesShareOneCategory() {
        given(applicationRepository.findMine(MEMBER_ID)).willReturn(List.of(
                application("카카오", ApplicationStage.INTERVIEW_FIRST, ApplicationResult.IN_PROGRESS),
                application("네이버", ApplicationStage.INTERVIEW_FINAL, ApplicationResult.IN_PROGRESS)));
        givenWeeklyStudy(session(PlanCategory.INTERVIEW, 60));

        assertThat(applicationService.currentFocus(MEMBER_ID, TODAY)).singleElement()
                .satisfies(row -> {
                    assertThat(row.category()).isEqualTo(PlanCategory.INTERVIEW);
                    assertThat(row.applicationCount()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("곧 마감인 것은 2주 안쪽으로 묻는다 - D-Day 와 같은 눈높이")
    void upcomingWindow() {
        applicationService.findUpcoming(MEMBER_ID, TODAY);

        org.mockito.BDDMockito.then(applicationRepository).should()
                .findUpcoming(MEMBER_ID, TODAY.plusDays(14));
    }
}
