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

    @Test
    @DisplayName("notify - 수신자에게 알림을 저장하고 해당 회원에게만 SSE 전송한다")
    void notify_savesAndSendsToRecipient() {
        given(memberRepository.getReferenceById(1L)).willReturn(member());
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(1L, "동주님이 플랜을 공유했습니다", "/plans/shared");

        then(notificationRepository).should().save(any(Notification.class));
        then(emitterRegistry).should().send(eq(1L), eq("notification"), any());
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
        then(emitterRegistry).should().send(eq(1L), anyString(), any());
        then(emitterRegistry).should().send(eq(3L), anyString(), any());
        then(emitterRegistry).should(never()).send(eq(2L), anyString(), any());
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
