package com.example.board.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** 접속 중인 SSE 클라이언트를 회원별로 관리하고, 특정 회원에게 이벤트를 전송한다 */
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    /** memberId → (emitterId → emitter). 같은 회원이 여러 탭으로 접속할 수 있다 */
    private final Map<Long, Map<Long, SseEmitter>> emittersByMember = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public SseEmitter add(Long memberId) {
        long emitterId = sequence.incrementAndGet();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Map<Long, SseEmitter> emitters =
                emittersByMember.computeIfAbsent(memberId, key -> new ConcurrentHashMap<>());
        emitters.put(emitterId, emitter);
        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> emitters.remove(emitterId));
        emitter.onError(e -> emitters.remove(emitterId));

        try {
            // 연결 직후 더미 이벤트를 보내야 클라이언트가 연결 성공을 인지한다
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitterId);
        }
        return emitter;
    }

    /**
     * 방금 연결된 이미터에만 이벤트를 보낸다 (재연결 시 놓친 알림 재전송용).
     * 브라우저가 마지막으로 받은 id 를 기억하도록 eventId 를 함께 실어 보낸다.
     */
    public void sendTo(SseEmitter emitter, String eventName, String eventId, Object data) {
        try {
            emitter.send(SseEmitter.event().id(eventId).name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    /** 해당 회원의 모든 접속(탭)으로 이벤트를 보낸다. 접속 중이 아니면 조용히 무시한다 */
    public void send(Long memberId, String eventName, String eventId, Object data) {
        Map<Long, SseEmitter> emitters = emittersByMember.get(memberId);
        if (emitters == null) {
            return;
        }
        emitters.forEach((emitterId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().id(eventId).name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitterId); // 끊어진 클라이언트 정리
            }
        });
    }

    /**
     * 지금 접속 중인 회원 id.
     *
     * <p>전체 공개 알림은 회원 수만큼 행을 만들지만, <b>실시간으로 밀어 줄 대상은 접속 중인 몇 명</b>뿐이다.
     * 저장은 한 번에 하고 전송은 이 목록에만 하기 위해 필요하다.</p>
     */
    public Set<Long> connectedMemberIds() {
        return emittersByMember.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    int activeCount() {
        return emittersByMember.values().stream().mapToInt(Map::size).sum();
    }
}
