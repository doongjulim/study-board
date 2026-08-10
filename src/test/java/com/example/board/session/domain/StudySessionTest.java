package com.example.board.session.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StudySession 은 실제로 공부한 시간을 기록한다")
class StudySessionTest {

    private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 8, 10, 9, 0);

    private Member owner() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Plan plan(PlanCategory category) {
        return new Plan("알고리즘", null, owner(), category,
                LocalDate.of(2026, 8, 10), LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    @Nested
    @DisplayName("시작")
    class Start {

        @Test
        @DisplayName("시작하면 진행 중 상태가 되고 아직 기록된 시간은 없다")
        void startsRunning() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            assertThat(session.isRunning()).isTrue();
            assertThat(session.getEndedAt()).isNull();
            assertThat(session.minutes()).isZero();
        }

        @Test
        @DisplayName("학습일은 시작 시각의 날짜로 정해진다 (자정 기준)")
        void studyDateFollowsStartedAt() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR,
                    LocalDateTime.of(2026, 8, 10, 23, 50));

            assertThat(session.getStudyDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        }

        @Test
        @DisplayName("자정을 넘겨 시작하면 그날(다음 날) 학습으로 기록된다")
        void afterMidnightBelongsToNextDay() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR,
                    LocalDateTime.of(2026, 8, 11, 0, 30));

            assertThat(session.getStudyDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        }

        @Test
        @DisplayName("계획과 함께 시작하면 분류는 계획의 분류를 따른다")
        void categoryFollowsPlan() {
            StudySession session = StudySession.start(owner(), plan(PlanCategory.CODING_TEST),
                    PlanCategory.ETC, NINE_AM);

            assertThat(session.getCategory()).isEqualTo(PlanCategory.CODING_TEST);
        }

        @Test
        @DisplayName("계획 없이도 시작할 수 있다 (계획에 없던 공부도 기록되어야 한다)")
        void startsWithoutPlan() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.INTERVIEW, NINE_AM);

            assertThat(session.getPlan()).isNull();
            assertThat(session.getCategory()).isEqualTo(PlanCategory.INTERVIEW);
        }

        @Test
        @DisplayName("계획도 분류도 없으면 기타(ETC)로 기록된다")
        void defaultsToEtc() {
            StudySession session = StudySession.start(owner(), null, null, NINE_AM);

            assertThat(session.getCategory()).isEqualTo(PlanCategory.ETC);
        }
    }

    @Nested
    @DisplayName("종료")
    class Stop {

        @Test
        @DisplayName("종료하면 실제 경과 시간이 분 단위로 남는다")
        void recordsElapsedMinutes() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            session.stop(NINE_AM.plusMinutes(95));

            assertThat(session.isRunning()).isFalse();
            assertThat(session.minutes()).isEqualTo(95);
        }

        @Test
        @DisplayName("계획한 시간이 아니라 실제 앉아 있던 시간이 기록된다")
        void recordsActualNotPlanned() {
            // 09:00~10:00(60분)으로 계획했지만 5분 만에 끝낸 경우
            StudySession session = StudySession.start(owner(), plan(PlanCategory.CODING_TEST),
                    null, NINE_AM);

            session.stop(NINE_AM.plusMinutes(5));

            assertThat(session.minutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("이미 종료된 세션은 다시 종료할 수 없다")
        void cannotStopTwice() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(30));

            assertThatThrownBy(() -> session.stop(NINE_AM.plusMinutes(60)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("시작보다 이른 시각으로는 종료할 수 없다")
        void cannotEndBeforeStart() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            assertThatThrownBy(() -> session.stop(NINE_AM.minusMinutes(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("사용자가 직접 종료하면 6시간을 넘겨도 그대로 인정한다")
        void longSessionIsKeptWhenStoppedByUser() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            session.stop(NINE_AM.plusHours(8));

            assertThat(session.minutes()).isEqualTo(480);
            assertThat(session.isAbandoned()).isFalse();
        }
    }

    @Nested
    @DisplayName("방치 세션 자동 종료")
    class CloseAbandoned {

        @Test
        @DisplayName("켜두고 잊은 세션은 최대 길이까지만 인정하고 방치로 표시한다")
        void closesAtMaxDuration() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            session.closeAsAbandoned();

            assertThat(session.isRunning()).isFalse();
            assertThat(session.isAbandoned()).isTrue();
            assertThat(session.minutes()).isEqualTo(StudySession.MAX_DURATION.toMinutes());
        }

        @Test
        @DisplayName("이미 종료된 세션은 방치 처리 대상이 아니다")
        void alreadyStoppedIsRejected() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(30));

            assertThatThrownBy(session::closeAsAbandoned)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("수동 보정")
    class Adjust {

        @Test
        @DisplayName("타이머를 깜빡한 경우 시작·종료 시각을 직접 고칠 수 있다")
        void adjustsTimes() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(10));

            session.adjust(NINE_AM.plusHours(1), NINE_AM.plusHours(3));

            assertThat(session.getStartedAt()).isEqualTo(NINE_AM.plusHours(1));
            assertThat(session.minutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("보정하면 학습일도 새 시작 시각을 따라간다")
        void recalculatesStudyDate() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(10));

            session.adjust(LocalDateTime.of(2026, 8, 11, 1, 0), LocalDateTime.of(2026, 8, 11, 2, 0));

            assertThat(session.getStudyDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        }

        @Test
        @DisplayName("보정하면 방치 표시가 해제된다 (사용자가 직접 확인했으므로)")
        void clearsAbandonedFlag() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.closeAsAbandoned();

            session.adjust(NINE_AM, NINE_AM.plusMinutes(50));

            assertThat(session.isAbandoned()).isFalse();
            assertThat(session.minutes()).isEqualTo(50);
        }

        @Test
        @DisplayName("종료가 시작보다 빠르면 보정할 수 없다")
        void rejectsInvertedRange() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(10));

            assertThatThrownBy(() -> session.adjust(NINE_AM.plusHours(2), NINE_AM.plusHours(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("진행 중 경과 시간")
    class Elapsed {

        @Test
        @DisplayName("진행 중인 세션은 현재 시각 기준 경과 초를 알려준다")
        void elapsedSeconds() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            assertThat(session.elapsedSeconds(NINE_AM.plusMinutes(2))).isEqualTo(120);
        }

        @Test
        @DisplayName("종료된 세션의 경과 초는 기록된 구간으로 고정된다")
        void elapsedOfStoppedSession() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);
            session.stop(NINE_AM.plusMinutes(30));

            assertThat(session.elapsedSeconds(NINE_AM.plusHours(5))).isEqualTo(1800);
        }
    }

    @Nested
    @DisplayName("소유권")
    class Ownership {

        @Test
        @DisplayName("본인 세션인지 확인할 수 있다")
        void checksOwner() {
            StudySession session = StudySession.start(owner(), null, PlanCategory.MAJOR, NINE_AM);

            assertThat(session.isOwnedBy(1L)).isTrue();
            assertThat(session.isOwnedBy(999L)).isFalse();
        }
    }
}
