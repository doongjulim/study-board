package com.example.board.stats.service;

import com.example.board.group.domain.GroupMember;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.service.StudyGroupService;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import com.example.board.stats.domain.GroupRanking;
import com.example.board.stats.domain.WeeklyChallenge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class GroupStatsServiceTest {

    /** 2026-08-12 는 수요일 - 주 시작(월요일)은 8월 10일이다 */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Mock StudyGroupService studyGroupService;
    @Mock StudySessionRepository sessionRepository;

    @InjectMocks GroupStatsService groupStatsService;

    private Member member(Long id, String nickname, int goal) {
        Member member = new Member("user" + id, "encoded-password", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "dailyGoalMinutes", goal);
        return member;
    }

    private GroupMember membership(Member member) {
        StudyGroup group = new StudyGroup("코테", null, member, "ABCD2345");
        return new GroupMember(group, member);
    }

    /** 지정한 분만큼 공부하고 끝난 기록 */
    private StudySession session(Member owner, LocalDate date, long minutes) {
        LocalDateTime startedAt = date.atTime(9, 0);
        StudySession session = StudySession.start(owner, null, PlanCategory.ETC, startedAt);
        session.stop(startedAt.plusMinutes(minutes));
        return session;
    }

    @Test
    @DisplayName("멤버별 학습 시간을 합산해 순위를 매긴다")
    void ranksMembersByWeeklyMinutes() {
        Member first = member(1L, "가", 30);
        Member second = member(2L, "나", 30);
        given(studyGroupService.findMembers(10L))
                .willReturn(List.of(membership(first), membership(second)));
        given(sessionRepository.findByOwner_IdInAndStudyDateBetween(any(), eq(MONDAY), eq(MONDAY.plusDays(6))))
                .willReturn(List.of(
                        session(first, MONDAY, 60),
                        session(first, MONDAY.plusDays(1), 30),
                        session(second, MONDAY, 120)));

        GroupRanking ranking = groupStatsService.weeklyRanking(10L, 1L, WEDNESDAY);

        assertThat(ranking.rows()).extracting(GroupRanking.Row::nickname).containsExactly("나", "가");
        assertThat(ranking.rows()).extracting(GroupRanking.Row::minutes).containsExactly(120L, 90L);
    }

    @Test
    @DisplayName("주중 아무 날로 물어도 그 주 월요일부터 일요일까지를 본다")
    void alwaysAggregatesMondayToSunday() {
        given(studyGroupService.findMembers(10L)).willReturn(List.of(membership(member(1L, "가", 30))));
        given(sessionRepository.findByOwner_IdInAndStudyDateBetween(any(), any(), any()))
                .willReturn(List.of());

        groupStatsService.weeklyRanking(10L, 1L, WEDNESDAY);

        then(sessionRepository).should()
                .findByOwner_IdInAndStudyDateBetween(List.of(1L), MONDAY, MONDAY.plusDays(6));
    }

    @Test
    @DisplayName("한 번도 켜지 않은 사람도 0분으로 순위표에 남는다 - 사라지면 빠진 것처럼 보인다")
    void memberWithoutSessionsStaysInRanking() {
        Member active = member(1L, "가", 30);
        Member idle = member(2L, "나", 30);
        given(studyGroupService.findMembers(10L))
                .willReturn(List.of(membership(active), membership(idle)));
        given(sessionRepository.findByOwner_IdInAndStudyDateBetween(any(), any(), any()))
                .willReturn(List.of(session(active, MONDAY, 60)));

        GroupRanking ranking = groupStatsService.weeklyRanking(10L, 1L, WEDNESDAY);

        assertThat(ranking.rows()).hasSize(2);
        assertThat(ranking.rows()).extracting(GroupRanking.Row::minutes).containsExactly(60L, 0L);
    }

    @Test
    @DisplayName("진행 중인 세션은 아직 0분이라 순위에 얹히지 않는다")
    void runningSessionCountsAsZero() {
        Member owner = member(1L, "가", 30);
        StudySession running = StudySession.start(owner, null, PlanCategory.ETC, MONDAY.atTime(9, 0));
        given(studyGroupService.findMembers(10L)).willReturn(List.of(membership(owner)));
        given(sessionRepository.findByOwner_IdInAndStudyDateBetween(any(), any(), any()))
                .willReturn(List.of(running));

        GroupRanking ranking = groupStatsService.weeklyRanking(10L, 1L, WEDNESDAY);

        assertThat(ranking.rows().get(0).minutes()).isZero();
    }

    @Test
    @DisplayName("멤버가 없으면 세션을 조회하지 않는다 (빈 in 절은 쿼리 오류)")
    void emptyGroupSkipsQuery() {
        given(studyGroupService.findMembers(10L)).willReturn(List.of());

        GroupRanking ranking = groupStatsService.weeklyRanking(10L, 1L, WEDNESDAY);

        assertThat(ranking.rows()).isEmpty();
        then(sessionRepository).should(never()).findByOwner_IdInAndStudyDateBetween(any(), any(), any());
    }

    @Test
    @DisplayName("주간 챌린지는 각자의 하루 목표를 주간으로 환산해 달성 인원을 센다")
    void weeklyChallengeCountsAchievers() {
        Member achiever = member(1L, "가", 30);   // 30 × 7 = 210분
        Member behind = member(2L, "나", 60);     // 60 × 7 = 420분
        given(studyGroupService.findMembers(10L))
                .willReturn(List.of(membership(achiever), membership(behind)));
        given(sessionRepository.findByOwner_IdInAndStudyDateBetween(any(), any(), any()))
                .willReturn(List.of(
                        session(achiever, MONDAY, 240),
                        session(behind, MONDAY, 100)));

        WeeklyChallenge challenge = groupStatsService.weeklyChallenge(10L, WEDNESDAY);

        assertThat(challenge.weekStart()).isEqualTo(MONDAY);
        assertThat(challenge.totalMinutes()).isEqualTo(340);
        assertThat(challenge.achievedCount()).isEqualTo(1);
        assertThat(challenge.challengerCount()).isEqualTo(2);
    }
}
