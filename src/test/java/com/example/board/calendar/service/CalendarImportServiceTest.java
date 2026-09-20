package com.example.board.calendar.service;

import com.example.board.calendar.service.CalendarImportService.ImportSummary;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("캘린더 가져오기")
class CalendarImportServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock PlanRepository planRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks CalendarImportService importService;

    private Member author() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    @BeforeEach
    void setUp() {
        given(memberRepository.getReferenceById(MEMBER_ID)).willReturn(author());
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                eq(MEMBER_ID), any(), any())).willReturn(List.of());
    }

    private String calendar(String... events) {
        return "BEGIN:VCALENDAR\r\n" + String.join("\r\n", events) + "\r\nEND:VCALENDAR\r\n";
    }

    private String event(String summary, String date) {
        return "BEGIN:VEVENT\r\nSUMMARY:" + summary
                + "\r\nDTSTART;VALUE=DATE:" + date + "\r\nEND:VEVENT";
    }

    @SuppressWarnings("unchecked")
    private List<Plan> savedPlans() {
        ArgumentCaptor<List<Plan>> saved = ArgumentCaptor.forClass(List.class);
        then(planRepository).should().saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("일정을 계획으로 저장한다. 분류는 '기타' 다 - 남의 캘린더에는 우리 분류 체계가 없다")
    void importsEvents() {
        ImportSummary summary = importService.importIcs(MEMBER_ID,
                calendar(event("자바 스터디", "20260914"), event("모의 면접", "20260916")));

        assertThat(summary.imported()).isEqualTo(2);
        assertThat(savedPlans())
                .extracting(Plan::getTitle, Plan::getPlanDate, Plan::getCategory)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(
                                "자바 스터디", LocalDate.of(2026, 9, 14), PlanCategory.ETC),
                        org.assertj.core.api.Assertions.tuple(
                                "모의 면접", LocalDate.of(2026, 9, 16), PlanCategory.ETC));
    }

    @Test
    @DisplayName("이미 있는 계획은 건너뛴다 - 같은 파일을 두 번 올리는 일은 흔하다")
    void skipsDuplicates() {
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                eq(MEMBER_ID), any(), any()))
                .willReturn(List.of(new Plan("자바 스터디", null, author(), PlanCategory.MAJOR,
                        LocalDate.of(2026, 9, 14), null, null)));

        ImportSummary summary = importService.importIcs(MEMBER_ID,
                calendar(event("자바 스터디", "20260914"), event("모의 면접", "20260916")));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.duplicated()).isEqualTo(1);
        assertThat(savedPlans()).singleElement()
                .satisfies(plan -> assertThat(plan.getTitle()).isEqualTo("모의 면접"));
    }

    @Test
    @DisplayName("같은 파일 안의 중복도 함께 걸러진다")
    void skipsDuplicatesWithinTheFile() {
        ImportSummary summary = importService.importIcs(MEMBER_ID,
                calendar(event("자바 스터디", "20260914"), event("자바 스터디", "20260914")));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.duplicated()).isEqualTo(1);
    }

    @Test
    @DisplayName("제목이 컬럼 상한을 넘으면 잘라서 넣는다 - 우리 폼의 검증을 거치지 않는 경로다")
    void cutsOverlongTitle() {
        String tooLong = "가".repeat(Plan.MAX_TITLE_LENGTH + 50);

        importService.importIcs(MEMBER_ID, calendar(event(tooLong, "20260914")));

        assertThat(savedPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.getTitle()).hasSize(Plan.MAX_TITLE_LENGTH);
            assertThat(plan.getTitle()).endsWith("…");   // 잘렸다는 것이 보이게
        });
    }

    @Test
    @DisplayName("못 가져온 것을 함께 알려 준다 - 말해 주지 않으면 없는 일정을 있다고 믿는다")
    void reportsSkipped() {
        ImportSummary summary = importService.importIcs(MEMBER_ID, calendar(
                event("단발 특강", "20260914"),
                "BEGIN:VEVENT\r\nSUMMARY:매주 스터디\r\nDTSTART;VALUE=DATE:20260915"
                        + "\r\nRRULE:FREQ=WEEKLY\r\nEND:VEVENT"));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.message()).contains("1개를 가져왔습니다").contains("가져오지 못했습니다");
    }

    @Test
    @DisplayName("가져올 것이 없으면 조회도 저장도 하지 않는다")
    void nothingToImport() {
        ImportSummary summary = importService.importIcs(MEMBER_ID, "그냥 텍스트 파일입니다");

        assertThat(summary.imported()).isZero();
        assertThat(summary.message()).contains("찾지 못했습니다");
        then(planRepository).shouldHaveNoInteractions();
    }
}
