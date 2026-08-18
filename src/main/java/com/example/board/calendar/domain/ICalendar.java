package com.example.board.calendar.domain;

import com.example.board.plan.domain.Plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 계획을 iCalendar(RFC 5545) 본문으로 옮긴다.
 *
 * <p>외부 라이브러리를 쓰지 않는 이유는 필요한 것이 VEVENT 몇 줄뿐이기 때문이다.
 * 대신 규격에서 실제로 깨지기 쉬운 두 가지 - <b>이스케이프</b>와 <b>줄 접기</b> - 를 지킨다.
 * 제목에 쉼표 하나만 들어가도 이스케이프를 빼먹으면 구글 캘린더가 그 일정을 통째로 버린다.</p>
 *
 * <p>시각은 시간대를 붙이지 않은 floating time 으로 낸다. 개인 플래너의 "오전 10시" 는
 * 사용자가 어디에 있든 그 지역의 10시라는 뜻이고, TZID 를 붙이면 여행 중에 시간이 밀린다.</p>
 */
public final class ICalendar {

    /** RFC 5545 는 한 줄을 75 옥텟으로 제한한다 */
    private static final int LINE_OCTET_LIMIT = 75;
    private static final String CRLF = "\r\n";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private ICalendar() {
    }

    public static String render(List<Plan> plans, String calendarName) {
        StringBuilder ical = new StringBuilder();
        append(ical, "BEGIN:VCALENDAR");
        append(ical, "VERSION:2.0");
        append(ical, "PRODID:-//study-board//study planner//KO");
        append(ical, "CALSCALE:GREGORIAN");
        append(ical, "METHOD:PUBLISH");
        append(ical, "X-WR-CALNAME:" + escape(calendarName));
        plans.forEach(plan -> appendEvent(ical, plan));
        append(ical, "END:VCALENDAR");
        return ical.toString();
    }

    private static void appendEvent(StringBuilder ical, Plan plan) {
        append(ical, "BEGIN:VEVENT");
        // 같은 계획을 다시 내려받아도 새 일정이 생기지 않도록 id 로 고정한다
        append(ical, "UID:plan-%d@study-board".formatted(plan.getId()));
        append(ical, "DTSTAMP:" + plan.getPlanDate().atStartOfDay().format(DATE_TIME));
        appendPeriod(ical, plan);
        append(ical, "SUMMARY:" + escape(summary(plan)));
        if (plan.getContent() != null && !plan.getContent().isBlank()) {
            append(ical, "DESCRIPTION:" + escape(plan.getContent()));
        }
        append(ical, "CATEGORIES:" + escape(plan.getCategory().getLabel()));
        append(ical, "END:VEVENT");
    }

    /**
     * 시간이 없는 종일 일정은 DATE 값으로 낸다.
     * 종일 일정의 DTEND 는 다음 날이어야 한다 - 규격상 끝 날짜는 포함되지 않기 때문이다.
     */
    private static void appendPeriod(StringBuilder ical, Plan plan) {
        LocalDate date = plan.getPlanDate();
        if (plan.getStartTime() == null) {
            append(ical, "DTSTART;VALUE=DATE:" + date.format(DATE));
            append(ical, "DTEND;VALUE=DATE:" + date.plusDays(1).format(DATE));
            return;
        }
        append(ical, "DTSTART:" + date.atTime(plan.getStartTime()).format(DATE_TIME));
        // 끝 시각이 없으면 시작만 있는 셈이라 같은 시각으로 닫는다 (구글은 DTEND 없는 일정을 싫어한다)
        append(ical, "DTEND:" + date.atTime(
                plan.getEndTime() != null ? plan.getEndTime() : plan.getStartTime()).format(DATE_TIME));
    }

    /** 끝낸 계획은 제목만 보고도 알 수 있게 표시한다 (VEVENT 에는 완료 상태가 없다) */
    private static String summary(Plan plan) {
        return plan.isCompleted() ? "✅ " + plan.getTitle() : plan.getTitle();
    }

    /** RFC 5545 의 TEXT 이스케이프. 순서가 중요하다 - 역슬래시를 먼저 바꿔야 이중 처리되지 않는다 */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * 75 옥텟이 넘는 줄은 접어서 다음 줄을 공백으로 시작한다.
     * 한글은 UTF-8 에서 3바이트라 25자만 넘어도 접히므로, 글자 중간에서 잘리지 않게 코드포인트 단위로 센다.
     */
    static void append(StringBuilder ical, String line) {
        int octets = 0;
        boolean folded = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            int size = utf8Length(codePoint);
            // 접힌 줄은 맨 앞 공백 한 칸을 이미 쓰고 있다
            int limit = folded ? LINE_OCTET_LIMIT - 1 : LINE_OCTET_LIMIT;

            if (octets + size > limit) {
                ical.append(folded ? " " : "").append(current).append(CRLF);
                current.setLength(0);
                octets = 0;
                folded = true;
            }
            current.appendCodePoint(codePoint);
            octets += size;
            i += charCount;
        }
        ical.append(folded ? " " : "").append(current).append(CRLF);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return (codePoint < 0x10000) ? 3 : 4;
    }
}
