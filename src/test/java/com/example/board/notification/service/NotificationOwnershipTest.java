package com.example.board.notification.service;

import com.example.board.member.domain.Member;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.board.member.repository.MemberRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * 알림은 수신자 본인에게만 보이는 개인 데이터다.
 *
 * <p>id 만 바꿔서 남의 알림을 읽음 처리하거나 지울 수 있으면 안 된다. 그래서 조회 단계에서
 * 수신자까지 함께 걸고, 찾지 못하면 "없다" 가 아니라 접근 거부로 답한다 -
 * 없는 것과 남의 것을 구분해 주면 그 자체로 존재 여부가 새어 나간다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationOwnershipTest {

    private static final long ME = 1L;
    private static final long SOMEONE_ELSE = 999L;

    @Mock NotificationRepository notificationRepository;
    @Mock MemberRepository memberRepository;
    @Mock SseEmitterRegistry emitterRegistry;

    @InjectMocks NotificationService notificationService;

    private Notification notification(long id) {
        Member recipient = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(recipient, "id", ME);
        Notification notification = new Notification(recipient, "댓글이 달렸습니다", "/posts/1");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @Nested
    @DisplayName("읽음 처리")
    class MarkAsRead {

        @Test
        @DisplayName("본인 알림은 읽음으로 바뀐다")
        void ownNotification() {
            Notification notification = notification(5L);
            given(notificationRepository.findByIdAndRecipient_Id(5L, ME))
                    .willReturn(Optional.of(notification));

            notificationService.markAsRead(ME, 5L);

            assertThat(notification.isReadFlag()).isTrue();
        }

        @Test
        @DisplayName("남의 알림은 접근이 거부된다")
        void othersNotification() {
            given(notificationRepository.findByIdAndRecipient_Id(5L, SOMEONE_ELSE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(SOMEONE_ELSE, 5L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("본인 알림은 지워진다")
        void ownNotification() {
            Notification notification = notification(5L);
            given(notificationRepository.findByIdAndRecipient_Id(5L, ME))
                    .willReturn(Optional.of(notification));

            notificationService.delete(ME, 5L);

            then(notificationRepository).should().delete(notification);
        }

        @Test
        @DisplayName("남의 알림은 지워지지 않는다")
        void othersNotification() {
            given(notificationRepository.findByIdAndRecipient_Id(5L, SOMEONE_ELSE))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.delete(SOMEONE_ELSE, 5L))
                    .isInstanceOf(AccessDeniedException.class);
            then(notificationRepository).should(never()).delete(any(Notification.class));
        }

        @Test
        @DisplayName("모두 삭제는 내 알림만 지운다")
        void deleteAllTargetsOwnerOnly() {
            notificationService.deleteAll(ME);

            then(notificationRepository).should().deleteByRecipient_Id(ME);
        }
    }

    @Nested
    @DisplayName("목록·정리")
    class ListAndCleanup {

        @Test
        @DisplayName("전체 보기는 내 알림만 페이지로 가져온다")
        void findPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Notification> page = new PageImpl<>(List.of(notification(5L)));
            given(notificationRepository.findByRecipient_IdOrderByIdDesc(ME, pageable))
                    .willReturn(page);

            assertThat(notificationService.findPage(ME, pageable)).isSameAs(page);
        }

        @Test
        @DisplayName("정리 기준은 그대로 저장소에 전달된다 - 보존 기간을 서비스가 몰래 바꾸지 않는다")
        void deleteOldPassesThresholds() {
            LocalDateTime readBefore = LocalDateTime.of(2025, 12, 10, 4, 20);
            LocalDateTime anyBefore = LocalDateTime.of(2025, 9, 11, 4, 20);
            given(notificationRepository.deleteOld(readBefore, anyBefore)).willReturn(7);

            assertThat(notificationService.deleteOld(readBefore, anyBefore)).isEqualTo(7);
        }
    }
}
