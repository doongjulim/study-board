package com.example.board.notification.service;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock SseEmitterRegistry emitterRegistry;

    @InjectMocks NotificationService notificationService;

    @Test
    @DisplayName("notify - 알림을 저장하고 SSE 로 브로드캐스트한다")
    void notify_savesAndBroadcasts() {
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify("동주님이 플랜을 공유했습니다", "/plans/shared");

        then(notificationRepository).should().save(any(Notification.class));
        then(emitterRegistry).should().broadcast(eq("notification"), any());
    }

    @Test
    @DisplayName("markAllAsRead - 읽지 않은 알림을 모두 읽음 처리한다")
    void markAllAsRead() {
        Notification n1 = new Notification("알림1", "/plans/daily");
        Notification n2 = new Notification("알림2", "/plans/shared");
        given(notificationRepository.findByReadFlagFalse()).willReturn(List.of(n1, n2));

        notificationService.markAllAsRead();

        assertThat(n1.isReadFlag()).isTrue();
        assertThat(n2.isReadFlag()).isTrue();
    }

    @Test
    @DisplayName("findRecent - 최근 알림 조회를 저장소에 위임한다")
    void findRecent() {
        given(notificationRepository.findTop10ByOrderByIdDesc()).willReturn(List.of());

        notificationService.findRecent();

        then(notificationRepository).should().findTop10ByOrderByIdDesc();
    }
}
