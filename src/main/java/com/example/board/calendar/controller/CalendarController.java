package com.example.board.calendar.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.calendar.service.CalendarFeedService;
import com.example.board.calendar.service.CalendarImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/calendar")
public class CalendarController {

    /** 캘린더 파일의 상한 - 전역 업로드 상한(10MB)은 첨부 이미지를 기준으로 정한 값이라 따로 좁힌다 */
    private static final long MAX_ICS_BYTES = 2L * 1024 * 1024;

    private final CalendarFeedService calendarFeedService;
    private final CalendarImportService calendarImportService;
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

    /**
     * .ics 파일을 읽어 계획으로 들여온다.
     *
     * <p>내보내기만 있고 반대 방향이 없어, 학원 시간표를 손으로 옮겨 적어야 했다.
     * 한쪽만 있는 연동은 반쪽이다.</p>
     *
     * <p>― 왜 크기를 따로 막는가<br>
     * 전역 업로드 상한은 10MB 인데, 그건 첨부 이미지를 기준으로 정한 값이다.
     * 캘린더 파일 하나가 그만큼일 이유가 없고, 큰 텍스트를 통째로 메모리에 올려 파싱하는 경로라
     * 여기서 한 번 더 좁힌다.</p>
     */
    @PostMapping("/import")
    public String importIcs(@RequestParam("file") MultipartFile file,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("message", readIcs(file)
                .map(ics -> calendarImportService.importIcs(principal.id(), ics).message())
                .orElse("캘린더 파일(.ics)을 %dMB 이하로 올려 주세요.".formatted(MAX_ICS_BYTES / (1024 * 1024))));
        return "redirect:/me";
    }

    /**
     * 올린 파일을 문자열로 읽는다. 받아들일 수 없으면 비어 있는 값을 준다.
     *
     * <p>확장자는 올리는 쪽이 정하는 값이라 그것만으로 판단하지 않는다 - 내용이 캘린더인지는
     * 파서가 보고, 여기서는 <b>크기와 비어 있음</b>만 본다(첨부 이미지에서 배운 것과 같은 방향이다).</p>
     */
    private Optional<String> readIcs(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_ICS_BYTES) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @PostMapping("/token/revoke")
    public String revokeToken(@AuthenticationPrincipal MemberPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        calendarFeedService.revokeToken(principal.id());
        redirectAttributes.addFlashAttribute("message", "구독을 끊었습니다.");
        return "redirect:/me";
    }
}
