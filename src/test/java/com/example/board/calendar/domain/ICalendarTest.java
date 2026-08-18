package com.example.board.calendar.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("iCalendar 내보내기")
class ICalendarTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(Long id, String title, LocalTime start, LocalTime end) {
        Plan plan = new Plan(title, null, author, PlanCategory.CODING_TEST, DATE, start, end);
        ReflectionTestUtils.setField(plan, "id", id);
        return plan;
    }

    @Test
    @DisplayName("VCALENDAR 로 감싸고 계획마다 VEVENT 를 만든다")
    void wrapsEventsInCalendar() {
        String ical = ICalendar.render(List.of(
                plan(1L, "알고리즘", LocalTime.of(10, 0), LocalTime.of(12, 0)),
                plan(2L, "면접 준비", LocalTime.of(14, 0), null)), "내 플래너");

        assertThat(ical).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ical.split("BEGIN:VEVENT", -1)).hasSize(3); // 앞부분 + 이벤트 2개
        assertThat(ical).contains("X-WR-CALNAME:내 플래너");
    }

    @Test
    @DisplayName("같은 계획을 다시 내려받아도 새 일정이 생기지 않도록 UID 를 id 로 고정한다")
    void uidIsStable() {
        String ical = ICalendar.render(List.of(plan(7L, "알고리즘", LocalTime.of(10, 0), null)), "내 플래너");

        assertThat(ical).contains("UID:plan-7@study-board");
    }

    @Test
    @DisplayName("시간이 있는 일정은 시간대를 붙이지 않는다 - 여행 중에도 그 지역의 10시여야 한다")
    void timedEventUsesFloatingTime() {
        String ical = ICalendar.render(
                List.of(plan(1L, "알고리즘", LocalTime.of(10, 0), LocalTime.of(12, 30))), "내 플래너");

        assertThat(ical).contains("DTSTART:20260812T100000");
        assertThat(ical).contains("DTEND:20260812T123000");
        assertThat(ical).doesNotContain("TZID");
    }

    @Test
    @DisplayName("종일 일정의 끝 날짜는 다음 날이다 - 규격상 끝 날짜는 포함되지 않는다")
    void allDayEventEndsNextDay() {
        String ical = ICalendar.render(List.of(plan(1L, "쉬는 날", null, null)), "내 플래너");

        assertThat(ical).contains("DTSTART;VALUE=DATE:20260812");
        assertThat(ical).contains("DTEND;VALUE=DATE:20260813");
    }

    @Test
    @DisplayName("끝 시각이 없으면 시작 시각으로 닫는다 - DTEND 가 없으면 구글이 일정을 버린다")
    void openEndedEventClosesAtStart() {
        String ical = ICalendar.render(List.of(plan(1L, "스터디", LocalTime.of(9, 0), null)), "내 플래너");

        assertThat(ical).contains("DTSTART:20260812T090000");
        assertThat(ical).contains("DTEND:20260812T090000");
    }

    @Test
    @DisplayName("쉼표·세미콜론·역슬래시를 이스케이프한다 - 하나만 빠져도 일정이 통째로 버려진다")
    void escapesSpecialCharacters() {
        assertThat(ICalendar.escape("a,b")).isEqualTo("a\\,b");
        assertThat(ICalendar.escape("a;b")).isEqualTo("a\\;b");
        assertThat(ICalendar.escape("a\\b")).isEqualTo("a\\\\b");
        assertThat(ICalendar.escape("a\nb")).isEqualTo("a\\nb");
        assertThat(ICalendar.escape("a\r\nb")).isEqualTo("a\\nb");
    }

    @Test
    @DisplayName("역슬래시를 먼저 바꿔 이중 이스케이프를 피한다")
    void escapesBackslashFirst() {
        assertThat(ICalendar.escape("a\\,b")).isEqualTo("a\\\\\\,b");
    }

    @Test
    @DisplayName("완료한 계획은 제목으로 구분된다 (VEVENT 에는 완료 상태가 없다)")
    void completedPlanIsMarkedInSummary() {
        Plan done = plan(1L, "알고리즘", LocalTime.of(10, 0), null);
        done.toggleCompleted();

        assertThat(ICalendar.render(List.of(done), "내 플래너")).contains("SUMMARY:✅ 알고리즘");
    }

    @Test
    @DisplayName("모든 줄이 75 옥텟을 넘지 않는다 - 긴 한글 제목도 접어서 내보낸다")
    void foldsLongLines() {
        String longTitle = "정말 긴 제목을 가진 공부 계획입니다 ".repeat(5);
        String ical = ICalendar.render(List.of(plan(1L, longTitle, LocalTime.of(10, 0), null)), "내 플래너");

        assertThat(Arrays.stream(ical.split("\r\n")).toList())
                .allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length)
                        .isLessThanOrEqualTo(75));
    }

    @Test
    @DisplayName("접힌 줄은 공백으로 시작한다 - 그래야 앞 줄의 이어짐으로 읽힌다")
    void foldedLinesStartWithSpace() {
        String longTitle = "가".repeat(60);
        String ical = ICalendar.render(List.of(plan(1L, longTitle, LocalTime.of(10, 0), null)), "내 플래너");

        List<String> lines = Arrays.stream(ical.split("\r\n")).toList();
        assertThat(lines).anyMatch(line -> line.startsWith(" "));
        // 접힌 조각을 도로 이어 붙이면 원래 제목이 나온다
        assertThat(ical.replace("\r\n ", "")).contains("SUMMARY:" + longTitle);
    }

    @Test
    @DisplayName("한글이 글자 중간에서 잘리지 않는다")
    void doesNotSplitMultibyteCharacters() {
        String ical = ICalendar.render(
                List.of(plan(1L, "가".repeat(100), LocalTime.of(10, 0), null)), "내 플래너");

        assertThat(ical.replace("\r\n ", "")).contains("가".repeat(100));
        assertThat(ical).doesNotContain("�");
    }

    @Test
    @DisplayName("계획이 하나도 없어도 유효한 빈 달력을 만든다")
    void emptyCalendarIsStillValid() {
        String ical = ICalendar.render(List.of(), "내 플래너");

        assertThat(ical).contains("BEGIN:VCALENDAR", "END:VCALENDAR").doesNotContain("BEGIN:VEVENT");
    }
}
