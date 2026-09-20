package com.example.board.member.repository;

import com.example.board.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    /** 비밀번호 찾기 - 이메일로 계정을 찾는다 */
    Optional<Member> findByEmail(String email);

    /**
     * 플랜 공유 알림을 받기로 한 활성 회원 id.
     * 사람 수만큼 회원을 하나씩 불러 설정을 확인하지 않도록 조회 단계에서 거른다.
     */
    @Query("""
            select m.id from Member m
            where m.withdrawnAt is null
              and m.notificationPreference.planSharedEnabled = true
            """)
    List<Long> findIdsAllowingPlanSharedNotification();

    /** 위와 같되 후보를 좁혀서 - 그룹 공개 fanout 은 같은 그룹 사람만 대상으로 한다 */
    @Query("""
            select m.id from Member m
            where m.id in :candidateIds
              and m.withdrawnAt is null
              and m.notificationPreference.planSharedEnabled = true
            """)
    List<Long> findIdsAllowingPlanSharedNotificationIn(@Param("candidateIds") Collection<Long> candidateIds);

    /**
     * 주간 리포트 알림을 받기로 한 활성 회원 id.
     *
     * <p>일요일 저녁에 한 번 도는 스케줄러가 쓴다. 회원을 통째로 불러오지 않고 id 만 가져오는 이유는,
     * 실제로 알림이 가는 사람은 <b>그 주에 기록이 있는 사람</b>뿐이라 대부분이 걸러지기 때문이다.</p>
     */
    @Query("""
            select m.id from Member m
            where m.withdrawnAt is null
              and m.notificationPreference.weeklyReportEnabled = true
            """)
    List<Long> findIdsAllowingWeeklyReportNotification();

    /** 캘린더 구독 - 로그인 없이 들어오는 요청이라 토큰만으로 주인을 찾는다 */
    Optional<Member> findByCalendarToken(String calendarToken);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    /**
     * 소셜 로그인 회원 조회.
     *
     * <p>이메일이 아니라 <b>제공자 + 제공자가 준 id</b> 로 찾는다. 이메일은 바뀌고,
     * 제공자가 주지 않을 수도 있으며(카카오는 동의 항목이다), 무엇보다
     * "같은 이메일이면 같은 사람" 으로 이으면 남의 계정을 가져가는 길이 열린다.</p>
     */
    Optional<Member> findByOauthProviderAndOauthProviderId(String oauthProvider, String oauthProviderId);

}
