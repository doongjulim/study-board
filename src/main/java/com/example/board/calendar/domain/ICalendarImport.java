package com.example.board.calendar.domain;

import com.example.board.common.time.ServiceZone;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * iCalendar(.ics) 본문을 읽어 가져올 일정으로 바꾼다.
 *
 * <p>― 왜 필요한가<br>
 * 내보내기(구독 주소·CSV)는 있는데 <b>반대 방향이 없었다.</b> 학원 시간표나 스터디 일정을
 * 손으로 옮겨 적어야 했고, 그러면 결국 옮기지 않게 된다. 한쪽만 있는 연동은 반쪽이다.
 *
 * <p>― 왜 직접 읽는가<br>
 * {@link ICalendar} 가 쓰는 쪽에서 이미 같은 판단을 했다 - 필요한 것이 VEVENT 몇 줄뿐이라
 * 라이브러리를 들이지 않는다. 다만 읽는 쪽은 <b>남이 만든 파일</b>을 받으므로 쓸 때보다 방어가 더 필요하다.
 * 규격에서 실제로 부딪히는 것들만 지킨다:
 *
 * <ul>
 *   <li><b>줄 접기</b> - 공백이나 탭으로 시작하는 줄은 앞 줄의 계속이다. 펴지 않으면 긴 제목이 잘린다</li>
 *   <li><b>속성 파라미터</b> - {@code DTSTART;VALUE=DATE:20260914}, {@code DTSTART;TZID=...:...}</li>
 *   <li><b>시간대</b> - {@code Z} 로 끝나면 UTC 다. 그대로 쓰면 아홉 시간 어긋난 일정이 들어온다</li>
 *   <li><b>이스케이프</b> - {@code \,} {@code \;} {@code \n} 을 되돌린다</li>
 * </ul>
 *
 * <p>― 무엇을 가져오지 않는가<br>
 * 반복 일정({@code RRULE})은 건너뛰고 몇 개를 건너뛰었는지만 알려 준다. 반복 규칙을 제대로 펴려면
 * 예외 날짜·무한 반복·시간대별 경계까지 다뤄야 하는데, 그 복잡함을 <b>조용히 틀리게</b> 처리하면
 * 사용자는 없는 일정을 믿게 된다. 못 가져온 것을 말해 주는 편이 낫다.
 *
 * <p>여러 날에 걸친 일정은 <b>시작한 날</b>의 계획 하나로 들어온다. 플래너의 한 줄은 하루의 할 일이고,
 * 한 일정이 날짜 수만큼 줄로 불어나면 가져오기 한 번에 그 달이 가득 찬다.
 */
public final class ICalendarImport {

    /** 한 번에 가져올 수 있는 최대 개수 - 남이 만든 파일이라 크기를 가정할 수 없다 */
    public static final int MAX_EVENTS = 300;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private ICalendarImport() {
    }

    /**
     * 가져올 일정과, 가져오지 못한 것의 수.
     *
     * @param events  가져올 일정
     * @param skipped 반복 규칙이 있거나 날짜를 읽을 수 없어 건너뛴 수
     */
    public record Result(List<ImportedEvent> events, int skipped) {
    }

    /**
     * 하나의 일정.
     *
     * @param startTime 종일 일정이면 null - 플래너의 '종일' 과 같은 뜻이다
     */
    public record ImportedEvent(String title, String description, LocalDate date,
                                LocalTime startTime, LocalTime endTime) {
    }

    public static Result parse(String ics) {
        if (ics == null || ics.isBlank()) {
            return new Result(List.of(), 0);
        }

        List<ImportedEvent> events = new ArrayList<>();
        int skipped = 0;
        for (List<String> block : eventBlocks(unfold(ics))) {
            if (events.size() >= MAX_EVENTS) {
                skipped++;
                continue;
            }
            ImportedEvent event = toEvent(block);
            if (event == null) {
                skipped++;
                continue;
            }
            events.add(event);
        }
        return new Result(List.copyOf(events), skipped);
    }

    /**
     * 접힌 줄을 편다.
     *
     * <p>규격은 75 옥텟이 넘는 줄을 접고 다음 줄을 공백이나 탭으로 시작하게 한다
     * ({@link ICalendar#append} 가 내보낼 때 하는 일의 반대다). 펴지 않으면 긴 제목이
     * 중간에서 잘린 채 들어오고, 접힌 뒷부분은 알 수 없는 속성으로 버려진다.</p>
     */
    private static List<String> unfold(String ics) {
        List<String> lines = new ArrayList<>();
        for (String raw : ics.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t') && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
                continue;
            }
            lines.add(raw);
        }
        return lines;
    }

    private static List<List<String>> eventBlocks(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line.trim())) {
                current = new ArrayList<>();
            } else if ("END:VEVENT".equalsIgnoreCase(line.trim())) {
                if (current != null) {
                    blocks.add(current);
                }
                current = null;
            } else if (current != null) {
                current.add(line);
            }
        }
        return blocks;
    }

    private static ImportedEvent toEvent(List<String> block) {
        String summary = null;
        String description = null;
        String dtStart = null;
        String dtStartParams = "";
        String dtEnd = null;
        String dtEndParams = "";

        for (String line : block) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String head = line.substring(0, colon);
            String value = line.substring(colon + 1);
            int semicolon = head.indexOf(';');
            String name = (semicolon < 0 ? head : head.substring(0, semicolon)).trim().toUpperCase();
            // 파라미터는 대문자로 바꾸지 않는다 - TZID 값(Asia/Seoul)은 대소문자를 가린다.
            // 이름만 대문자로 견주고 값은 적힌 그대로 쓴다.
            String params = semicolon < 0 ? "" : head.substring(semicolon + 1);

            switch (name) {
                case "SUMMARY" -> summary = unescape(value);
                case "DESCRIPTION" -> description = unescape(value);
                case "DTSTART" -> { dtStart = value.trim(); dtStartParams = params; }
                case "DTEND" -> { dtEnd = value.trim(); dtEndParams = params; }
                // 반복은 조용히 틀리게 펴느니 건너뛴다 - 사용자가 없는 일정을 믿게 되는 편이 더 나쁘다
                case "RRULE", "RDATE" -> {
                    return null;
                }
                default -> { /* 나머지 속성은 플래너에 옮길 자리가 없다 */ }
            }
        }

        if (dtStart == null || summary == null || summary.isBlank()) {
            return null;   // 언제인지 또는 무엇인지 모르는 일정은 계획이 될 수 없다
        }

        LocalDateTime start = parseMoment(dtStart, dtStartParams);
        if (start == null) {
            return null;
        }
        boolean allDay = isDateOnly(dtStart, dtStartParams);
        LocalDateTime end = (dtEnd == null) ? null : parseMoment(dtEnd, dtEndParams);

        return new ImportedEvent(summary.trim(), blankToNull(description), start.toLocalDate(),
                allDay ? null : start.toLocalTime(),
                endTimeOf(allDay, start, end));
    }

    /**
     * 끝 시각. 종일 일정에는 없고, 날짜가 넘어가는 일정은 비워 둔다.
     *
     * <p>자정을 넘겨 끝나는 일정에 끝 시각을 그대로 넣으면 "10:00 ~ 02:00" 이 되어
     * 계획 시간이 음수가 된다 - 그런 값이 통계로 흘러 들어가면 그 주가 통째로 틀어진다.</p>
     */
    private static LocalTime endTimeOf(boolean allDay, LocalDateTime start, LocalDateTime end) {
        if (allDay || end == null || !end.toLocalDate().equals(start.toLocalDate())) {
            return null;
        }
        return end.toLocalTime();
    }

    private static boolean isDateOnly(String value, String params) {
        return params.toUpperCase().contains("VALUE=DATE") || value.length() == 8;
    }

    /**
     * 값을 서비스 시간대의 시각으로 바꾼다.
     *
     * <p>{@code Z} 로 끝나면 UTC 다 - 구글이 내보내는 파일이 대개 그렇다. 그대로 읽으면
     * 오전 10시 수업이 새벽 1시로 들어온다. {@code TZID} 가 있으면 그 시간대로 읽는다.
     * 둘 다 없으면 floating time 이라 적힌 그대로가 맞다({@link ICalendar} 가 내보내는 방식이다).</p>
     */
    private static LocalDateTime parseMoment(String value, String params) {
        try {
            if (isDateOnly(value, params)) {
                return LocalDate.parse(value.substring(0, 8), DATE).atStartOfDay();
            }
            if (value.endsWith("Z")) {
                return ZonedDateTime.of(LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME),
                        ZoneId.of("UTC")).withZoneSameInstant(ServiceZone.ZONE).toLocalDateTime();
            }
            LocalDateTime local = LocalDateTime.parse(value, DATE_TIME);
            ZoneId zone = zoneOf(params);
            return (zone == null) ? local
                    : ZonedDateTime.of(local, zone).withZoneSameInstant(ServiceZone.ZONE).toLocalDateTime();
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;   // 읽을 수 없는 날짜는 건너뛴 것으로 센다 - 파일 하나 때문에 전부 실패하지 않게
        }
    }

    private static ZoneId zoneOf(String params) {
        for (String param : params.split(";")) {
            String trimmed = param.trim();
            if (trimmed.regionMatches(true, 0, "TZID=", 0, "TZID=".length())) {
                try {
                    // 값에서 따옴표를 벗긴다 - TZID="Asia/Seoul" 로 적는 도구가 있다
                    return ZoneId.of(trimmed.substring("TZID=".length()).trim().replace("\"", ""));
                } catch (Exception e) {
                    return null;   // 모르는 시간대 이름이면 적힌 그대로 읽는다
                }
            }
        }
        return null;
    }

    /** {@link ICalendar#escape} 의 반대. 역슬래시를 마지막에 풀어야 이중 처리되지 않는다 */
    static String unescape(String text) {
        return text.replace("\\n", "\n").replace("\\N", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
