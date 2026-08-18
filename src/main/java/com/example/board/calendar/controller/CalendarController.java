package com.example.board.calendar.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.calendar.service.CalendarFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarFeedService calendarFeedService;
    private final Clock clock;

    /**
     * 구독 주소. 구글 캘린더가 로그인 없이 주기적으로 읽어 가므로 인증을 걸지 않는다
     * (주소에 든 토큰이 곧 열쇠다 - SecurityConfig 에서 이 경로만 열어 둔다).
     */
    @GetMapping(value = "/{token}.ics", produces = "text/calendar;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<byte[]> feed(@PathVariable String token) {
        byte[] body = calendarFeedService.renderFeed(token).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                // 캘린더 앱이 옛 내용을 붙들지 않도록 - 계획은 자주 바뀐다
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(body);
    }

    /** 내려받기 - 로그인한 본인 계획만 나간다 */
    @GetMapping("/export.csv")
    @ResponseBody
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal MemberPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        // 기간을 고르지 않으면 최근 석 달 - 처음 눌러 본 사람이 빈 파일을 받지 않게 한다
        LocalDate start = (from != null) ? from : today.minusMonths(3);
        LocalDate end = (to != null) ? to : today;

        byte[] body = calendarFeedService.renderCsv(principal.id(), start, end)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"study-plans-%s_%s.csv\"".formatted(start, end))
                .body(body);
    }

    @PostMapping("/token")
    public String issueToken(@AuthenticationPrincipal MemberPrincipal principal,
                             RedirectAttributes redirectAttributes) {
        calendarFeedService.issueToken(principal.id());
        redirectAttributes.addFlashAttribute("message",
                "구독 주소를 발급했습니다. 이전 주소로 등록해 둔 캘린더는 더 이상 갱신되지 않습니다.");
        return "redirect:/me";
    }

    @PostMapping("/token/revoke")
    public String revokeToken(@AuthenticationPrincipal MemberPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        calendarFeedService.revokeToken(principal.id());
        redirectAttributes.addFlashAttribute("message", "구독을 끊었습니다.");
        return "redirect:/me";
    }
}
