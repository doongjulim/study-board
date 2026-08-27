package com.example.board.session.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.session.dto.RunningSessionResponse;
import com.example.board.session.dto.StoppedSessionResponse;
import com.example.board.session.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 학습 타이머 API.
 *
 * <p>페이지를 이동해도 헤더 타이머가 유지되어야 하므로 화면 이동(리다이렉트)이 아니라
 * JSON 으로 응답하고, 화면 갱신은 {@code static/js/timer.js} 가 맡는다.</p>
 *
 * <p>현재 시각은 {@link Clock} 으로 주입받아 테스트에서 고정할 수 있게 한다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/sessions")
public class StudySessionController {

    private final StudySessionService studySessionService;
    private final Clock clock;

    /** 진행 중인 세션 - 없으면 204 로 응답해 화면이 타이머를 감춘다 */
    @GetMapping("/current")
    public ResponseEntity<RunningSessionResponse> current(@AuthenticationPrincipal MemberPrincipal principal) {
        return studySessionService.findRunning(principal.id())
                .map(session -> ResponseEntity.ok(RunningSessionResponse.from(session, now())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 학습 시작 - planId 없이 category 만으로도 시작할 수 있다 */
    @PostMapping("/start")
    public ResponseEntity<RunningSessionResponse> start(@RequestParam(required = false) Long planId,
                                                        @RequestParam(required = false) PlanCategory category,
                                                        @AuthenticationPrincipal MemberPrincipal principal) {
        LocalDateTime now = now();
        RunningSessionResponse response = RunningSessionResponse.from(
                studySessionService.start(principal.id(), planId, category, now), now);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/stop")
    public StoppedSessionResponse stop(@PathVariable Long id,
                                       @AuthenticationPrincipal MemberPrincipal principal) {
        return StoppedSessionResponse.from(studySessionService.stop(id, principal.id(), now()));
    }

    /** 수동 보정 - 타이머를 깜빡했거나 방치로 자동 종료된 기록을 바로잡는다 */
    @PostMapping("/{id}/adjust")
    public StoppedSessionResponse adjust(@PathVariable Long id,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                         LocalDateTime startedAt,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                         LocalDateTime endedAt,
                                         @AuthenticationPrincipal MemberPrincipal principal) {
        return StoppedSessionResponse.from(
                studySessionService.adjust(id, principal.id(), startedAt, endedAt));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal MemberPrincipal principal) {
        studySessionService.delete(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
