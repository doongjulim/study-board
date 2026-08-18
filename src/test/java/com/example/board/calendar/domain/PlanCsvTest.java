package com.example.board.calendar.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.ShareScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CSV 내보내기")
class PlanCsvTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private final Member author = new Member("tester1", "encoded-password", "테스터");

    private Plan plan(String title, String content, LocalTime start, LocalTime end) {
        return new Plan(title, content, author, PlanCategory.CODING_TEST, DATE, start, end);
    }

    @Test
    @DisplayName("엑셀이 한글을 깨뜨리지 않도록 BOM 으로 시작한다")
    void startsWithBom() {
        String csv = PlanCsv.render(List.of());

        assertThat(csv).startsWith(PlanCsv.BOM);
        assertThat(csv).contains("날짜,시작,종료,분류,제목,메모,완료,공유 범위");
    }

    @Test
    @DisplayName("계획 한 줄이 한 행이 된다")
    void writesOneRowPerPlan() {
        String csv = PlanCsv.render(List.of(plan("알고리즘", "스택 복습", LocalTime.of(10, 0), LocalTime.of(12, 0))));

        assertThat(csv).contains("\"2026-08-12\",\"10:00\",\"12:00\",\"코딩테스트\",\"알고리즘\",\"스택 복습\",\"미완료\",\"비공개\"");
    }

    @Test
    @DisplayName("종일 일정은 시각 칸을 비운다")
    void allDayLeavesTimesEmpty() {
        String csv = PlanCsv.render(List.of(plan("쉬는 날", null, null, null)));

        assertThat(csv).contains("\"2026-08-12\",\"\",\"\",");
    }

    @Test
    @DisplayName("완료 여부와 공유 범위를 사람이 읽는 말로 적는다")
    void writesReadableStatus() {
        Plan done = plan("알고리즘", null, LocalTime.of(10, 0), null);
        done.toggleCompleted();
        done.changeShareScope(ShareScope.GROUP);

        assertThat(PlanCsv.render(List.of(done))).contains("\"완료\",\"그룹 공개\"");
    }

    @Test
    @DisplayName("값 안의 큰따옴표는 두 번 겹쳐 이스케이프한다")
    void escapesQuotes() {
        assertThat(PlanCsv.quote("그가 \"안녕\" 이라 했다"))
                .isEqualTo("\"그가 \"\"안녕\"\" 이라 했다\"");
    }

    @Test
    @DisplayName("쉼표·줄바꿈이 든 제목도 한 칸으로 유지된다")
    void keepsCommaAndNewlineInsideOneField() {
        String csv = PlanCsv.render(List.of(plan("알고리즘, 자료구조", "1번\n2번", LocalTime.of(10, 0), null)));

        assertThat(csv).contains("\"알고리즘, 자료구조\"");
        assertThat(csv).contains("\"1번\n2번\"");
    }

    @Test
    @DisplayName("모든 칸을 감싼다 - 조건을 두면 언젠가 빠뜨린다")
    void quotesEveryField() {
        assertThat(PlanCsv.quote("보통값")).isEqualTo("\"보통값\"");
        assertThat(PlanCsv.quote("")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("계획이 없어도 헤더만 있는 파일을 만든다")
    void emptyExportKeepsHeader() {
        String csv = PlanCsv.render(List.of());

        assertThat(csv.strip()).endsWith("공유 범위");
    }
}
