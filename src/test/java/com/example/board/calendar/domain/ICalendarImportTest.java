package com.example.board.calendar.domain;

import com.example.board.calendar.domain.ICalendarImport.ImportedEvent;
import com.example.board.calendar.domain.ICalendarImport.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("캘린더 가져오기 파싱")
class ICalendarImportTest {

    private String calendar(String... eventLines) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"
                + String.join("\r\n", eventLines) + "\r\n"
                + "END:VCALENDAR\r\n";
    }

    private String event(String... lines) {
        return "BEGIN:VEVENT\r\n" + String.join("\r\n", lines) + "\r\nEND:VEVENT";
    }

    @Test
    @DisplayName("시각이 있는 일정을 날짜·시작·종료로 읽는다")
    void timedEvent() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:자바 스터디",
                "DTSTART:20260914T190000",
                "DTEND:20260914T210000")));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.title()).isEqualTo("자바 스터디");
            assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 14));
            assertThat(e.startTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(e.endTime()).isEqualTo(LocalTime.of(21, 0));
        });
    }

    @Test
    @DisplayName("VALUE=DATE 인 종일 일정은 시각이 없다 - 플래너의 '종일' 과 같은 뜻이다")
    void allDayEvent() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:휴식",
                "DTSTART;VALUE=DATE:20260914",
                "DTEND;VALUE=DATE:20260915")));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 14));
            assertThat(e.startTime()).isNull();
            assertThat(e.endTime()).isNull();
        });
    }

    @Test
    @DisplayName("Z 로 끝나는 시각은 UTC 다 - 그대로 읽으면 오전 10시 수업이 새벽 1시로 들어온다")
    void utcIsConvertedToServiceZone() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:온라인 특강",
                "DTSTART:20260914T010000Z",
                "DTEND:20260914T030000Z")));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 14));
            assertThat(e.startTime()).isEqualTo(LocalTime.of(10, 0));   // KST = UTC+9
            assertThat(e.endTime()).isEqualTo(LocalTime.of(12, 0));
        });
    }

    @Test
    @DisplayName("TZID 가 있으면 그 시간대로 읽어 서비스 시간대로 옮긴다")
    void tzidIsHonoured() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:뉴욕 웨비나",
                "DTSTART;TZID=America/New_York:20260914T090000")));

        // 9/14 은 서머타임 기간이라 뉴욕은 UTC-4, 서울과 13시간 차이다
        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(LocalDate.of(2026, 9, 14));
            assertThat(e.startTime()).isEqualTo(LocalTime.of(22, 0));
        });
    }

    @Test
    @DisplayName("접힌 줄을 편다 - 펴지 않으면 긴 제목이 중간에서 잘린 채 들어온다")
    void unfoldsLines() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:알고리즘 스터디 - 다이나믹 프로그래밍 집중",
                " 반 (2주차)",
                "DTSTART:20260914T190000")));

        assertThat(result.events()).singleElement()
                .extracting(ImportedEvent::title)
                .isEqualTo("알고리즘 스터디 - 다이나믹 프로그래밍 집중반 (2주차)");
    }

    @Test
    @DisplayName("이스케이프를 되돌린다 - 쉼표·세미콜론·줄바꿈")
    void unescapesText() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:스터디\\, 2교시",
                "DESCRIPTION:1장\\n2장\\; 연습문제",
                "DTSTART:20260914T190000")));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.title()).isEqualTo("스터디, 2교시");
            assertThat(e.description()).isEqualTo("1장\n2장; 연습문제");
        });
    }

    @Test
    @DisplayName("반복 일정은 건너뛰고 몇 개인지 알려 준다 - 조용히 틀리게 펴느니 못 가져왔다고 말한다")
    void skipsRecurring() {
        Result result = ICalendarImport.parse(calendar(
                event("SUMMARY:매주 스터디", "DTSTART:20260914T190000", "RRULE:FREQ=WEEKLY;COUNT=10"),
                event("SUMMARY:단발 특강", "DTSTART:20260915T190000")));

        assertThat(result.events()).singleElement()
                .extracting(ImportedEvent::title).isEqualTo("단발 특강");
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("날짜나 제목이 없는 일정은 계획이 될 수 없다")
    void skipsIncomplete() {
        Result result = ICalendarImport.parse(calendar(
                event("DTSTART:20260914T190000"),                 // 무엇인지 모른다
                event("SUMMARY:언젠가"),                           // 언제인지 모른다
                event("SUMMARY:읽을 수 없는 날짜", "DTSTART:내일")));

        assertThat(result.events()).isEmpty();
        assertThat(result.skipped()).isEqualTo(3);
    }

    @Test
    @DisplayName("자정을 넘겨 끝나는 일정은 끝 시각을 비운다 - '10:00 ~ 02:00' 은 계획 시간이 음수가 된다")
    void dropsEndTimeCrossingMidnight() {
        Result result = ICalendarImport.parse(calendar(event(
                "SUMMARY:밤샘 코딩",
                "DTSTART:20260914T220000",
                "DTEND:20260915T020000")));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.startTime()).isEqualTo(LocalTime.of(22, 0));
            assertThat(e.endTime()).isNull();
        });
    }

    @Test
    @DisplayName("한 번에 가져오는 개수에 상한이 있다 - 남이 만든 파일이라 크기를 가정할 수 없다")
    void capsEventCount() {
        String[] events = new String[ICalendarImport.MAX_EVENTS + 5];
        for (int i = 0; i < events.length; i++) {
            events[i] = "BEGIN:VEVENT\r\nSUMMARY:일정 " + i
                    + "\r\nDTSTART;VALUE=DATE:20260914\r\nEND:VEVENT";
        }

        Result result = ICalendarImport.parse(calendar(events));

        assertThat(result.events()).hasSize(ICalendarImport.MAX_EVENTS);
        assertThat(result.skipped()).isEqualTo(5);
    }

    @Test
    @DisplayName("빈 파일이나 캘린더가 아닌 내용은 아무것도 가져오지 않는다 - 터지지는 않는다")
    void toleratesGarbage() {
        assertThat(ICalendarImport.parse(null).events()).isEmpty();
        assertThat(ICalendarImport.parse("").events()).isEmpty();
        assertThat(ICalendarImport.parse("그냥 텍스트 파일입니다").events()).isEmpty();
    }

    @Test
    @DisplayName("우리가 내보낸 파일을 그대로 다시 읽을 수 있다 - 한 바퀴가 돌아야 연동이다")
    void roundTripsOurOwnExport() {
        String ics = "BEGIN:VCALENDAR\r\n"
                + "BEGIN:VEVENT\r\n"
                + "DTSTART:20260914T100000\r\n"
                + "DTEND:20260914T120000\r\n"
                + "SUMMARY:" + ICalendar.escape("자료구조, 스택·큐") + "\r\n"
                + "END:VEVENT\r\nEND:VCALENDAR\r\n";

        assertThat(ICalendarImport.parse(ics).events()).singleElement()
                .extracting(ImportedEvent::title).isEqualTo("자료구조, 스택·큐");
    }
}
