package com.example.board.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.*;

class SseEmitterRegistryTest {

    @Test
    @DisplayName("add - 회원별로 이미터를 등록하면 활성 개수가 늘어난다")
    void add() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        registry.add(1L);
        registry.add(1L); // 같은 회원의 두 번째 탭
        registry.add(2L);

        assertThat(registry.activeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("완료(끊어진) 이미터는 send 시 정리된다")
    void removeCompletedEmitterOnSend() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        SseEmitter emitter = registry.add(1L);
        emitter.complete(); // 이후 send() 는 IllegalStateException 을 던진다

        registry.send(1L, "notification", "data");

        assertThat(registry.activeCount()).isZero();
    }

    @Test
    @DisplayName("send - 접속하지 않은 회원에게 보내도 예외가 발생하지 않는다")
    void sendWithoutEmitters() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        assertThatCode(() -> registry.send(99L, "notification", "data"))
                .doesNotThrowAnyException();
    }
}
