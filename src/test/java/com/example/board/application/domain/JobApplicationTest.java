package com.example.board.application.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.PlanCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobApplicationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    private Member owner() {
        return new Member("tester1", "encoded-password", "동주");
    }

    private JobApplication application(ApplicationStage stage, LocalDate deadline) {
        return new JobApplication(owner(), "카카오", "백엔드", stage, deadline, null);
    }

    @Test
    @DisplayName("등록 직후에는 진행 중이다 - '아직 결과가 없다' 는 null 이 아니라 하나의 상태다")
    void startsInProgress() {
        JobApplication application = application(ApplicationStage.DOCUMENT, null);

        assertThat(application.getResult()).isEqualTo(ApplicationResult.IN_PROGRESS);
        assertThat(application.isOngoing()).isTrue();
    }

    @Test
    @DisplayName("회사명은 비울 수 없다 - 어디에 넣었는지 모르는 지원은 기록이 아니다")
    void requiresCompany() {
        assertThatThrownBy(() -> new JobApplication(owner(), " ", null,
                ApplicationStage.DOCUMENT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("직무와 메모는 비워 두면 null 이다 - 빈 문자열과 '안 적었다' 를 섞지 않는다")
    void blankBecomesNull() {
        JobApplication application = new JobApplication(owner(), "카카오", "   ",
                ApplicationStage.DOCUMENT, null, "  ");

        assertThat(application.getPosition()).isNull();
        assertThat(application.getMemo()).isNull();
    }

    @Test
    @DisplayName("단계마다 하게 되는 공부가 정해져 있다 - 분류별 학습량이 비로소 기준을 갖는다")
    void stageCarriesStudyCategory() {
        assertThat(application(ApplicationStage.CODING_TEST, null).studyCategory())
                .isEqualTo(PlanCategory.CODING_TEST);
        assertThat(application(ApplicationStage.INTERVIEW_FIRST, null).studyCategory())
                .isEqualTo(PlanCategory.INTERVIEW);
        // 결과를 기다리는 동안 따로 준비할 것은 없다
        assertThat(application(ApplicationStage.RESULT, null).studyCategory()).isNull();
    }

    @Test
    @DisplayName("끝난 지원의 단계는 '지금 무엇을 준비해야 하나' 와 상관이 없다")
    void closedApplicationHasNoStudyCategory() {
        JobApplication application = application(ApplicationStage.CODING_TEST, null);

        application.update("카카오", "백엔드", ApplicationStage.CODING_TEST,
                ApplicationResult.FAILED, null, null);

        assertThat(application.studyCategory()).isNull();
        assertThat(application.isOngoing()).isFalse();
    }

    @Test
    @DisplayName("마감 표기는 D-Day 와 같은 모양이다 - 나란히 놓이는 값이 다르게 보이면 안 된다")
    void deadlineLabelMatchesDday() {
        assertThat(application(ApplicationStage.DOCUMENT, TODAY.plusDays(7)).deadlineLabel(TODAY))
                .isEqualTo("D-7");
        assertThat(application(ApplicationStage.DOCUMENT, TODAY).deadlineLabel(TODAY))
                .isEqualTo("D-DAY");
        assertThat(application(ApplicationStage.DOCUMENT, TODAY.minusDays(3)).deadlineLabel(TODAY))
                .isEqualTo("D+3");
    }

    @Test
    @DisplayName("마감일이 없으면 표기도 없다 - 모든 공고에 마감이 적혀 있는 것은 아니다")
    void noDeadlineNoLabel() {
        JobApplication application = application(ApplicationStage.DOCUMENT, null);

        assertThat(application.deadlineLabel(TODAY)).isNull();
        assertThat(application.remainingDays(TODAY)).isNull();
        assertThat(application.isUpcoming(TODAY, 14)).isFalse();
    }

    @Test
    @DisplayName("지난 마감이라도 진행 중이면 알린다 - 넘겼다는 사실 자체가 알아야 할 정보다")
    void overdueButOngoingIsStillUpcoming() {
        assertThat(application(ApplicationStage.DOCUMENT, TODAY.minusDays(2)).isUpcoming(TODAY, 14))
                .isTrue();
    }

    @Test
    @DisplayName("끝난 지원의 지난 마감일은 알릴 것이 아니다")
    void closedApplicationIsNotUpcoming() {
        JobApplication application = application(ApplicationStage.DOCUMENT, TODAY.plusDays(1));

        application.update("카카오", "백엔드", ApplicationStage.RESULT,
                ApplicationResult.PASSED, TODAY.plusDays(1), null);

        assertThat(application.isUpcoming(TODAY, 14)).isFalse();
    }

    @Test
    @DisplayName("수정에서 단계·결과를 비워 보내면 지금 값을 지킨다 - 잘못된 요청이 기록을 지우지 않게")
    void updateKeepsCurrentWhenNull() {
        JobApplication application = application(ApplicationStage.CODING_TEST, null);
        application.update("카카오", "백엔드", null, null, null, null);

        assertThat(application.getStage()).isEqualTo(ApplicationStage.CODING_TEST);
        assertThat(application.getResult()).isEqualTo(ApplicationResult.IN_PROGRESS);
    }

    @Test
    @DisplayName("본인 것만 다룰 수 있다")
    void ownership() {
        Member owner = owner();
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", 7L);
        JobApplication application = new JobApplication(owner, "카카오", null,
                ApplicationStage.DOCUMENT, null, null);

        assertThat(application.isOwnedBy(7L)).isTrue();
        assertThat(application.isOwnedBy(8L)).isFalse();
    }
}
