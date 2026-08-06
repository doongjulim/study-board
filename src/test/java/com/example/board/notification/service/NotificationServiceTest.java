package com.example.board.notification.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock MemberRepository memberRepository;
    @Mock SseEmitterRegistry emitterRegistry;

    @InjectMocks NotificationService notificationService;

    private Member member() {
        return new Member("tester1", "encoded-password", "테스터");
    }

    private Notification notification(long id, String message) {
        Notification notification = new Notification(member(), message, "/plans/daily");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Test
    @DisplayName("notify - 수신자에게 알림을 저장하고 해당 회원에게만 SSE 전송한다")
    void notify_savesAndSendsToRecipient() {
        given(memberRepository.getReferenceById(1L)).willReturn(member());
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(1L, "동주님이 플랜을 공유했습니다", "/plans/shared");

        then(notificationRepository).should().save(any(Notification.class));
        then(emitterRegistry).should().send(eq(1L), eq("notification"), anyString(), any());
    }

    @Test
    @DisplayName("notifyAllExcept - 발신자를 제외한 모든 회원에게 알림을 만든다")
    void notifyAllExcept() {
        given(memberRepository.findAllIds()).willReturn(List.of(1L, 2L, 3L));
        given(memberRepository.getReferenceById(anyLong())).willReturn(member());
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notifyAllExcept(2L, "공유 알림", "/plans/shared");

        then(notificationRepository).should(times(2)).save(any(Notification.class));
        then(emitterRegistry).should().send(eq(1L), anyString(), anyString(), any());
        then(emitterRegistry).should().send(eq(3L), anyString(), anyString(), any());
        then(emitterRegistry).should(never()).send(eq(2L), anyString(), anyString(), any());
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
