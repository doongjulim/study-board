package com.example.board.session.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.repository.PlanRepository;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudySessionService")
class StudySessionServiceTest {

    private static final Long MY_ID = 1L;
    private static final Long OTHER_ID = 999L;
    private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 8, 10, 9, 0);

    @Mock StudySessionRepository sessionRepository;
    @Mock MemberRepository memberRepository;
    @Mock PlanRepository planRepository;

    @InjectMocks StudySessionService studySessionService;

    private Member member(Long id, String loginId) {
        Member member = new Member(loginId, "encoded-password", loginId + "-닉");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Plan planOf(Member author, PlanCategory category) {
        Plan plan = new Plan("알고리즘", null, author, category,
                LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), LocalTime.of(10, 0));
        ReflectionTestUtils.setField(plan, "id", 10L);
        return plan;
    }

    private StudySession runningSession(Member owner) {
        StudySession session = StudySession.start(owner, null, PlanCategory.MAJOR, NINE_AM);
        ReflectionTestUtils.setField(session, "id", 100L);
        return session;
    }

    @Nested
    @DisplayName("시작")
    class Start {

        @Test
        @DisplayName("진행 중인 세션이 없으면 새 세션을 저장한다")
        void startsWhenIdle() {
            Member me = member(MY_ID, "tester1");
            given(sessionRepository.findByOwner_IdAndEndedAtIsNull(MY_ID)).willReturn(Optional.empty());
            given(memberRepository.getReferenceById(MY_ID)).willReturn(me);
            given(sessionRepository.save(any(StudySession.class))).willAnswer(inv -> inv.getArgument(0));

            studySessionService.start(MY_ID, null, PlanCategory.INTERVIEW, NINE_AM);

            ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
            then(sessionRepository).should().save(captor.capture());
            assertThat(captor.getValue().isRunning()).isTrue();
            assertThat(captor.getValue().getCategory()).isEqualTo(PlanCategory.INTERVIEW);
        }

        @Test
        @DisplayName("이미 진행 중이면 새로 시작할 수 없다 (합계가 실제 시간을 넘지 않도록)")
        void rejectsConcurrentStart() {
            given(sessionRepository.findByOwner_IdAndEndedAtIsNull(MY_ID))
                    .willReturn(Optional.of(runningSession(member(MY_ID, "tester1"))));

            assertThatThrownBy(() -> studySessionService.start(MY_ID, null, PlanCategory.MAJOR, NINE_AM))
                    .isInstanceOf(IllegalStateException.class);

            then(sessionRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("계획과 함께 시작하면 계획의 분류를 따른다")
        void followsPlanCategory() {
            Member me = member(MY_ID, "tester1");
            given(sessionRepository.findByOwner_IdAndEndedAtIsNull(MY_ID)).willReturn(Optional.empty());
            given(memberRepository.getReferenceById(MY_ID)).willReturn(me);
            given(planRepository.findById(10L)).willReturn(Optional.of(planOf(me, PlanCategory.CODING_TEST)));
            given(sessionRepository.save(any(StudySession.class))).willAnswer(inv -> inv.getArgument(0));

            StudySession saved = studySessionService.start(MY_ID, 10L, null, NINE_AM);

            assertThat(saved.getCategory()).isEqualTo(PlanCategory.CODING_TEST);
            assertThat(saved.hasPlan()).isTrue();
        }

        @Test
        @DisplayName("남의 계획으로는 학습을 시작할 수 없다")
        void rejectsOthersPlan() {
            given(sessionRepository.findByOwner_IdAndEndedAtIsNull(MY_ID)).willReturn(Optional.empty());
            given(memberRepository.getReferenceById(MY_ID)).willReturn(member(MY_ID, "tester1"));
            given(planRepository.findById(10L))
                    .willReturn(Optional.of(planOf(member(OTHER_ID, "other"), PlanCategory.MAJOR)));

            assertThatThrownBy(() -> studySessionService.start(MY_ID, 10L, null, NINE_AM))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("종료·보정·삭제")
    class Modify {

        @Test
        @DisplayName("종료하면 실제 경과 시간이 확정된다")
        void stopsSession() {
            StudySession session = runningSession(member(MY_ID, "tester1"));
            given(sessionRepository.findById(100L)).willReturn(Optional.of(session));

            StudySession stopped = studySessionService.stop(100L, MY_ID, NINE_AM.plusMinutes(45));

            assertThat(stopped.isRunning()).isFalse();
            assertThat(stopped.minutes()).isEqualTo(45);
        }

        @Test
        @DisplayName("남의 세션은 종료할 수 없다")
        void rejectsStoppingOthers() {
            given(sessionRepository.findById(100L))
                    .willReturn(Optional.of(runningSession(member(OTHER_ID, "other"))));

            assertThatThrownBy(() -> studySessionService.stop(100L, MY_ID, NINE_AM.plusMinutes(10)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("보정하면 시간이 새 구간으로 바뀐다")
        void adjustsSession() {
            StudySession session = runningSession(member(MY_ID, "tester1"));
            session.stop(NINE_AM.plusMinutes(5));
            given(sessionRepository.findById(100L)).willReturn(Optional.of(session));

            StudySession adjusted = studySessionService.adjust(
                    100L, MY_ID, NINE_AM, NINE_AM.plusMinutes(90));

            assertThat(adjusted.minutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("존재하지 않는 세션은 예외가 발생한다")
        void rejectsMissingSession() {
            given(sessionRepository.findById(404L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> studySessionService.findOwned(404L, MY_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("본인 세션은 삭제할 수 있다")
        void deletesOwnSession() {
            StudySession session = runningSession(member(MY_ID, "tester1"));
            given(sessionRepository.findById(100L)).willReturn(Optional.of(session));

            studySessionService.delete(100L, MY_ID);

            then(sessionRepository).should().delete(session);
        }
    }

    @Nested
    @DisplayName("방치 세션 정리")
    class CloseAbandoned {

        @Test
        @DisplayName("최대 길이를 넘겨 켜둔 세션을 자동 종료하고 건수를 반환한다")
        void closesStaleSessions() {
            StudySession stale = runningSession(member(MY_ID, "tester1"));
            LocalDateTime now = NINE_AM.plusHours(9);
            given(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(
                    now.minus(StudySession.MAX_DURATION))).willReturn(List.of(stale));

            int closed = studySessionService.closeAbandoned(now);

            assertThat(closed).isEqualTo(1);
            assertThat(stale.isRunning()).isFalse();
            assertThat(stale.isAbandoned()).isTrue();
            assertThat(stale.minutes()).isEqualTo(StudySession.MAX_DURATION.toMinutes());
        }

        @Test
        @DisplayName("정리 대상이 없으면 0 을 반환한다")
        void nothingToClose() {
            given(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(any()))
                    .willReturn(List.of());

            assertThat(studySessionService.closeAbandoned(NINE_AM)).isZero();
        }
    }
}
