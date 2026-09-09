package com.example.board.notification.domain;

import com.example.board.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 알림 메시지의 길이 규칙.
 *
 * <p>이 규칙이 없어서 "긴 댓글에 답글을 달면 답글이 저장되지 않는" 결함이 있었다.
 * 알림 저장은 답글과 같은 트랜잭션에서 일어나므로, 알림이 컬럼 상한에 걸리면 답글까지 함께 롤백된다.</p>
 */
class NotificationTest {

    private final Member recipient = new Member("reader", "encoded", "받는이");

    @Test
    @DisplayName("상한을 넘는 메시지는 잘려서 저장된다 - 부르는 쪽이 길이를 몰라도 실패하지 않는다")
    void abbreviatesLongMessage() {
        String tooLong = "가".repeat(Notification.MAX_MESSAGE_LENGTH + 100);

        Notification notification = new Notification(recipient, tooLong, "/posts/1");

        assertThat(notification.getMessage()).hasSize(Notification.MAX_MESSAGE_LENGTH);
        assertThat(notification.getMessage()).endsWith("…");
    }

    @Test
    @DisplayName("상한과 길이가 같으면 손대지 않는다 - 경계에서 멀쩡한 글자를 잘라내지 않는다")
    void keepsMessageAtExactLimit() {
        String exact = "나".repeat(Notification.MAX_MESSAGE_LENGTH);

        Notification notification = new Notification(recipient, exact, "/posts/1");

        assertThat(notification.getMessage()).isEqualTo(exact);
    }

    @Test
    @DisplayName("짧은 메시지는 그대로 둔다")
    void keepsShortMessage() {
        Notification notification = new Notification(recipient, "동주님이 댓글을 남겼습니다: 안녕", "/posts/1");

        assertThat(notification.getMessage()).isEqualTo("동주님이 댓글을 남겼습니다: 안녕");
    }

    @Test
    @DisplayName("null 은 null 로 둔다 - 길이 보정이 not null 위반을 감추면 원인이 흐려진다")
    void keepsNull() {
        assertThat(Notification.abbreviate(null)).isNull();
    }
}
