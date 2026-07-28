package com.example.board.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.*;

class SseEmitterRegistryTest {

    @Test
    @DisplayName("add - 이미터를 등록하면 활성 개수가 늘어난다")
    void add() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        registry.add();
        registry.add();

        assertThat(registry.activeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("완료(끊어진) 이미터는 브로드캐스트 시 정리된다")
    void removeCompletedEmitterOnBroadcast() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        SseEmitter emitter = registry.add();
        emitter.complete(); // 이후 send() 는 IllegalStateException 을 던진다

        registry.broadcast("notification", "data");

        assertThat(registry.activeCount()).isZero();
    }

    @Test
    @DisplayName("broadcast - 등록된 이미터가 없어도 예외가 발생하지 않는다")
    void broadcastWithoutEmitters() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        assertThatCode(() -> registry.broadcast("notification", "data"))
                .doesNotThrowAnyException();
    }
}
