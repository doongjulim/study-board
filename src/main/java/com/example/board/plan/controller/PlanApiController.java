package com.example.board.plan.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.plan.domain.DailyProgress;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.dto.DailyProgressResponse;
import com.example.board.plan.dto.PlanRowResponse;
import com.example.board.plan.dto.QuickPlanForm;
import com.example.board.plan.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 화면을 옮기지 않는 플래너 조작.
 *
 * <p>일정 하나 추가하려고 다른 페이지로 갔다 오고, 완료 체크마다 전체 화면이 다시 그려지면
 * 매일 쓰기 어렵다. 여기서는 바뀐 부분만 JSON 으로 돌려주고 화면 갱신은
 * {@code static/js/plans.js} 가 맡는다.</p>
 *
 * <p>기존 폼 방식({@link PlanController})도 그대로 남겨 둔다.
 * JS 가 동작하지 않는 환경에서도 플래너를 쓸 수 있어야 하기 때문이다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans")
public class PlanApiController {

    private final PlanService planService;

    /** 한 줄 입력으로 추가 - 추가된 행과 갱신된 진행 상황을 함께 돌려준다 */
    @PostMapping
    public ResponseEntity<QuickAddResponse> quickAdd(@Valid @RequestBody QuickPlanForm form,
                                                     @AuthenticationPrincipal MemberPrincipal principal) {
        Long id = planService.create(form.toPlanForm(), principal.id());
        Plan created = planService.findOwned(id, principal.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new QuickAddResponse(
                PlanRowResponse.from(created), progressOf(form.getPlanDate(), principal.id())));
    }

    /** 완료 상태 전환 */
    @PostMapping("/{id}/toggle")
    public ToggleResponse toggle(@PathVariable Long id,
                                 @AuthenticationPrincipal MemberPrincipal principal) {
        Plan plan = planService.toggleCompleted(id, principal.id());
        return new ToggleResponse(plan.getId(), plan.isCompleted(),
                progressOf(plan.getPlanDate(), principal.id()));
    }

    /** 못 끝낸 일정을 다른 날로 옮긴다 */
    @PostMapping("/rollover")
    public RolloverResponse rollover(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @AuthenticationPrincipal MemberPrincipal principal) {
        int moved = planService.rollover(principal.id(), from, to);
        return new RolloverResponse(moved, progressOf(to, principal.id()));
    }

    private DailyProgressResponse progressOf(LocalDate date, Long memberId) {
        List<Plan> plans = planService.findDaily(date, memberId);
        return DailyProgressResponse.from(DailyProgress.of(plans));
    }

    // ── 예외 처리 ────────────────────────────────────────────
    // GlobalExceptionHandler 는 HTML 오류 페이지를 반환하므로 JSON API 에는 맞지 않는다.

    /**
     * 입력값 오류. 이 핸들러가 없으면 GlobalExceptionHandler 의 포괄 처리에 걸려
     * 500 HTML 페이지가 내려간다 (JSON 을 기대하는 화면에서는 원인을 알 수 없다).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("입력값을 확인해 주세요.");
        return ResponseEntity.badRequest().body(message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /** 완료한 일정을 옮기려 한 경우 등 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    public record QuickAddResponse(PlanRowResponse plan, DailyProgressResponse progress) {
    }

    public record ToggleResponse(Long id, boolean completed, DailyProgressResponse progress) {
    }

    public record RolloverResponse(int movedCount, DailyProgressResponse progress) {
    }
}
