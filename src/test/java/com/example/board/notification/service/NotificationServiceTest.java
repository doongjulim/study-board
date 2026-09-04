package com.example.board.notification.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 9, 0);

    /**
     * fanout 이 쓰는 시각을 고정한다.
     *
     * <p>Clock 을 스텁으로 두고 필요한 테스트에서만 부른다 - 모든 테스트에 고정 시계를 주면
     * 쓰지 않는 스텁이 되어 Mockito 가 잡아낸다. LocalDateTime.now(clock) 은 instant() 와
     * getZone() 을 함께 부르므로 둘 다 준다.</p>
     */
    private void fixClock() {
        given(clock.instant()).willReturn(NOW.toInstant(ZoneOffset.UTC));
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @Mock NotificationRepository notificationRepository;
    @Mock MemberRepository memberRepository;
    @Mock SseEmitterRegistry emitterRegistry;
    /** fanout 이 "방금 넣은 것" 을 되찾는 표지로 쓰는 시각 */
    @Mock Clock clock;

    @InjectMocks NotificationService notificationService;

    private Member member() {
        return memberWithId(1L);
    }

    private Member memberWithId(long id) {
        Member member = new Member("tester" + id, "encoded-password", "테스터" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    /** 해당 종류의 알림을 꺼 둔 회원 */
    private Member memberWithNotificationsOff() {
        Member member = memberWithId(1L);
        member.changeNotificationPreference(false, 10, false, false);
        return member;
    }

    private Notification notification(long id, String message) {
        Notification notification = new Notification(member(), message, "/plans/daily");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    @DisplayName("notify - 수신자에게 알림을 저장하고 해당 회원에게만 SSE 전송한다")
    void notify_savesAndSendsToRecipient() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member()));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(1L, NotificationType.COMMENT, "댓글이 달렸습니다", "/posts/1");

        then(notificationRepository).should().save(any(Notification.class));
        then(emitterRegistry).should().send(eq(1L), eq("notification"), anyString(), any());
    }

    @Test
    @DisplayName("notify - 그 종류를 꺼 둔 회원에게는 알림을 남기지 않는다")
    void notify_respectsPreference() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(memberWithNotificationsOff()));

        notificationService.notify(1L, NotificationType.COMMENT, "댓글이 달렸습니다", "/posts/1");

        then(notificationRepository).should(never()).save(any());
        then(emitterRegistry).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("notify - 리마인더는 켜고 댓글만 꺼 둔 회원은 리마인더를 받는다")
    void notify_perTypeSetting() {
        Member member = memberWithId(1L);
        member.changeNotificationPreference(true, 10, true, false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(1L, NotificationType.REMINDER, "곧 시작해요", "/plans/daily");

        then(notificationRepository).should().save(any(Notification.class));
    }

    @Test
    @DisplayName("notify - 존재하지 않는 회원이면 조용히 넘어간다")
    void notify_unknownRecipient() {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        notificationService.notify(99L, NotificationType.COMMENT, "댓글", "/posts/1");

        then(notificationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("notifyAllExcept - 발신자를 뺀 사람들을 한 문장으로 넣는다 (한 건씩 insert 하지 않는다)")
    void notifyAllExcept() {
        fixClock();
        given(memberRepository.findIdsAllowingPlanSharedNotification()).willReturn(List.of(1L, 2L, 3L));
        given(emitterRegistry.connectedMemberIds()).willReturn(Set.of());

        notificationService.notifyAllExcept(2L, "공유 알림", "/plans/shared");

        // 회원이 만 명이면 만 번의 insert 가 되던 자리다
        then(notificationRepository).should()
                .insertForAll(eq(List.of(1L, 3L)), eq("공유 알림"), eq("/plans/shared"), eq(NOW));
        then(notificationRepository).should(never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("접속 중인 사람에게만 실시간으로 밀어 준다 - 저장은 모두, 전송은 몇 명")
    void notifyAllExcept_pushesToConnectedOnly() {
        fixClock();
        given(memberRepository.findIdsAllowingPlanSharedNotification()).willReturn(List.of(1L, 2L, 3L));
        given(emitterRegistry.connectedMemberIds()).willReturn(Set.of(3L));
        Notification pushed = notificationWithRecipient(3L);
        given(notificationRepository.findByRecipient_IdInAndCreatedAt(List.of(3L), NOW))
                .willReturn(List.of(pushed));

        notificationService.notifyAllExcept(2L, "공유 알림", "/plans/shared");

        then(emitterRegistry).should().send(eq(3L), anyString(), anyString(), any());
        then(emitterRegistry).should(never()).send(eq(1L), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("접속자가 없으면 되찾는 조회조차 하지 않는다")
    void notifyAllExcept_noConnected_skipsLookup() {
        fixClock();
        given(memberRepository.findIdsAllowingPlanSharedNotification()).willReturn(List.of(1L, 2L));
        given(emitterRegistry.connectedMemberIds()).willReturn(Set.of());

        notificationService.notifyAllExcept(2L, "공유 알림", "/plans/shared");

        then(notificationRepository).should(never())
                .findByRecipient_IdInAndCreatedAt(any(), any());
    }

    @Test
    @DisplayName("notifyMembersExcept - 지정한 사람 중 발신자만 빼고 넣는다 (그룹 공개)")
    void notifyMembersExcept() {
        fixClock();
        given(memberRepository.findIdsAllowingPlanSharedNotificationIn(List.of(1L, 2L, 3L)))
                .willReturn(List.of(1L, 2L, 3L));
        given(emitterRegistry.connectedMemberIds()).willReturn(Set.of());

        notificationService.notifyMembersExcept(List.of(1L, 2L, 3L), 2L, "공유 알림", "/plans/shared");

        then(notificationRepository).should()
                .insertForAll(eq(List.of(1L, 3L)), anyString(), anyString(), eq(NOW));
    }

    @Test
    @DisplayName("발신자를 빼고 나면 아무도 안 남는 경우 - 아무것도 넣지 않는다")
    void notifyAllExcept_onlySender() {
        given(memberRepository.findIdsAllowingPlanSharedNotification()).willReturn(List.of(2L));

        notificationService.notifyAllExcept(2L, "공유 알림", "/plans/shared");

        then(notificationRepository).should(never()).insertForAll(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notifyMembersExcept - 대상이 없으면 조회조차 하지 않는다 (빈 in 절은 쿼리 오류)")
    void notifyMembersExcept_emptyAudience() {
        notificationService.notifyMembersExcept(List.of(), 2L, "공유 알림", "/plans/shared");

        then(memberRepository).should(never()).findIdsAllowingPlanSharedNotificationIn(any());
        then(notificationRepository).should(never()).insertForAll(any(), anyString(), anyString(), any());
    }

    /** 전송 대상 확인용 - 수신자 id 만 있으면 된다 */
    private Notification notificationWithRecipient(long recipientId) {
        Notification notification = new Notification(memberWithId(recipientId), "공유 알림", "/plans/shared");
        ReflectionTestUtils.setField(notification, "id", 99L);
        return notification;
    }

    @Test
    @DisplayName("subscribe - Last-Event-ID 가 있으면 그 이후 놓친 알림을 이어서 보낸다")
    void subscribe_replaysMissed() {
        SseEmitter emitter = new SseEmitter();
        given(emitterRegistry.add(1L)).willReturn(emitter);
        given(notificationRepository.findByRecipient_IdAndIdGreaterThanOrderByIdAsc(1L, 5L))
                .willReturn(List.of(notification(6L, "놓친 알림1"), notification(7L, "놓친 알림2")));

        notificationService.subscribe(1L, "5");

        then(emitterRegistry).should().sendTo(eq(emitter), eq("notification"), eq("6"), any());
        then(emitterRegistry).should().sendTo(eq(emitter), eq("notification"), eq("7"), any());
    }

    @Test
    @DisplayName("subscribe - Last-Event-ID 가 없는 첫 연결이면 재전송하지 않는다")
    void subscribe_firstConnection() {
        given(emitterRegistry.add(1L)).willReturn(new SseEmitter());

        notificationService.subscribe(1L, null);

        then(notificationRepository).should(never())
                .findByRecipient_IdAndIdGreaterThanOrderByIdAsc(anyLong(), anyLong());
        then(emitterRegistry).should(never()).sendTo(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("subscribe - 숫자가 아닌 Last-Event-ID 는 무시하고 정상 연결한다")
    void subscribe_invalidLastEventId() {
        SseEmitter emitter = new SseEmitter();
        given(emitterRegistry.add(1L)).willReturn(emitter);

        assertThat(notificationService.subscribe(1L, "not-a-number")).isSameAs(emitter);

        then(notificationRepository).should(never())
                .findByRecipient_IdAndIdGreaterThanOrderByIdAsc(anyLong(), anyLong());
    }

    @Test
    @DisplayName("markAllAsRead - 해당 회원의 읽지 않은 알림만 읽음 처리한다")
    void markAllAsRead() {
        Notification n1 = new Notification(member(), "알림1", "/plans/daily");
        Notification n2 = new Notification(member(), "알림2", "/plans/shared");
        given(notificationRepository.findByRecipient_IdAndReadFlagFalse(1L)).willReturn(List.of(n1, n2));

        notificationService.markAllAsRead(1L);

        assertThat(n1.isReadFlag()).isTrue();
        assertThat(n2.isReadFlag()).isTrue();
    }

    @Test
    @DisplayName("findRecent - 해당 회원의 최근 알림 조회를 저장소에 위임한다")
    void findRecent() {
        given(notificationRepository.findTop10ByRecipient_IdOrderByIdDesc(1L)).willReturn(List.of());

        notificationService.findRecent(1L);

        then(notificationRepository).should().findTop10ByRecipient_IdOrderByIdDesc(1L);
    }
}
