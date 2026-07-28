package com.example.board.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 접속 중인 SSE 클라이언트를 관리하고 이벤트를 브로드캐스트한다 */
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public SseEmitter add() {
        long id = sequence.incrementAndGet();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));

        try {
            // 연결 직후 더미 이벤트를 보내야 클라이언트가 연결 성공을 인지한다
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitters.remove(id);
        }
        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(id); // 끊어진 클라이언트 정리
            }
        });
    }

    int activeCount() {
        return emitters.size();
    }
}
