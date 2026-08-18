package com.example.board.stats.service;

import com.example.board.group.domain.GroupMember;
import com.example.board.group.service.StudyGroupService;
import com.example.board.member.domain.Member;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import com.example.board.stats.domain.GroupRanking;
import com.example.board.stats.domain.MemberStudyTime;
import com.example.board.stats.domain.WeeklyChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 그룹의 이번 주 학습 현황을 모은다.
 *
 * <p>"누가 누구와 같은 그룹인가" 는 group 모듈이, "얼마나 공부했는가" 는 session 모듈이 안다.
 * 둘을 잇는 집계는 그 일을 업으로 하는 stats 모듈 한곳에서만 한다
 * (group 이 세션을 알게 되면 그룹은 더 이상 소속만 다루는 모듈이 아니게 된다).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupStatsService {

    private final StudyGroupService studyGroupService;
    private final StudySessionRepository sessionRepository;

    /** 그 날짜가 속한 주(월요일 시작)의 그룹 순위 */
    public GroupRanking weeklyRanking(Long groupId, Long viewerId, LocalDate anyDayOfWeek) {
        return GroupRanking.of(weeklyStudyTimes(groupId, anyDayOfWeek), viewerId, WeeklyChallenge.DAYS);
    }

    public WeeklyChallenge weeklyChallenge(Long groupId, LocalDate anyDayOfWeek) {
        return WeeklyChallenge.of(weeklyStudyTimes(groupId, anyDayOfWeek), weekStart(anyDayOfWeek));
    }

    /**
     * 멤버별 이번 주 학습 시간. 한 번도 켜지 않은 사람도 0분으로 남긴다 -
     * 순위표에서 이름이 사라지면 "빠졌나" 로 읽히고, 아무도 안 한 주에는 표가 통째로 비어 버린다.
     */
    private List<MemberStudyTime> weeklyStudyTimes(Long groupId, LocalDate anyDayOfWeek) {
        List<Member> members = studyGroupService.findMembers(groupId).stream()
                .map(GroupMember::getMember)
                .toList();
        if (members.isEmpty()) {
            return List.of();
        }

        LocalDate from = weekStart(anyDayOfWeek);
        Map<Long, Long> minutesByMember = sessionRepository
                .findByOwner_IdInAndStudyDateBetween(
                        members.stream().map(Member::getId).toList(), from, from.plusDays(6))
                .stream()
                .collect(Collectors.groupingBy(session -> session.getOwner().getId(),
                        Collectors.summingLong(StudySession::minutes)));

        return members.stream()
                .map(member -> new MemberStudyTime(
                        member.getId(),
                        member.getNickname(),
                        minutesByMember.getOrDefault(member.getId(), 0L),
                        member.getDailyGoalMinutes()))
                .toList();
    }

    private static LocalDate weekStart(LocalDate anyDayOfWeek) {
        return anyDayOfWeek.with(DayOfWeek.MONDAY);
    }
}
