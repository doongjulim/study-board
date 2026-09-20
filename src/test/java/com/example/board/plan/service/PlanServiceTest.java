package com.example.board.plan.service;

import com.example.board.group.service.StudyGroupService;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.RepeatType;
import com.example.board.plan.domain.ShareScope;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.event.PlanSharedEvent;
import com.example.board.plan.repository.PlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock PlanRepository planRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyGroupService studyGroupService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PlanService planService;

    private Member author() {
        Member member = new Member("tester1", "encoded-password", "동주");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member otherAuthor() {
        Member member = new Member("other", "encoded-password", "다른사람");
        ReflectionTestUtils.setField(member, "id", 999L);
        return member;
    }

    private PlanForm form() {
        PlanForm form = new PlanForm();
        form.setTitle("자료구조 공부");
        form.setContent("스택, 큐 복습");
        form.setPlanDate(LocalDate.of(2026, 7, 9));
        form.setStartTime(LocalTime.of(10, 0));
        form.setEndTime(LocalTime.of(12, 0));
        return form;
    }

    private Plan plan() {
        return new Plan("자료구조 공부", "스택, 큐 복습", author(), PlanCategory.CODING_TEST,
                LocalDate.of(2026, 7, 9), LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    // ── create / update / delete ─────────────────────────────

    @SuppressWarnings("unchecked")
    private void givenSaveAllAssignsIds() {
        given(planRepository.saveAll(any(Iterable.class))).willAnswer(inv -> {
            List<Plan> saved = new java.util.ArrayList<>();
            long nextId = 1L;
            for (Plan plan : (Iterable<Plan>) inv.getArgument(0)) {
                ReflectionTestUtils.setField(plan, "id", nextId++);
                saved.add(plan);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("create - 현재 회원을 작성자로 플랜을 저장하고 id 를 반환한다")
    void create() {
        Member author = author();
        given(memberRepository.getReferenceById(1L)).willReturn(author);
        givenSaveAllAssignsIds();

        Long id = planService.create(form(), 1L);

        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<Iterable<Plan>> captor = ArgumentCaptor.forClass(Iterable.class);
        then(planRepository).should().saveAll(captor.capture());
        List<Plan> saved = new java.util.ArrayList<>();
        captor.getValue().forEach(saved::add);
        assertThat(saved).singleElement().satisfies(plan -> {
            assertThat(plan.getTitle()).isEqualTo("자료구조 공부");
            assertThat(plan.getAuthor()).isSameAs(author);
            assertThat(plan.getPlanDate()).isEqualTo(LocalDate.of(2026, 7, 9));
            assertThat(plan.isPartOfSeries()).isFalse();
        });
    }

    @Test
    @DisplayName("create - 반복 설정이 있으면 종료일까지 만들고 같은 시리즈로 묶는다")
    void create_repeating() {
        given(memberRepository.getReferenceById(1L)).willReturn(author());
        givenSaveAllAssignsIds();

        PlanForm form = form();
        form.setRepeatType(RepeatType.DAILY);
        form.setRepeatUntil(LocalDate.of(2026, 7, 11)); // 7/9 ~ 7/11 → 3건

        planService.create(form, 1L);

        ArgumentCaptor<Iterable<Plan>> captor = ArgumentCaptor.forClass(Iterable.class);
        then(planRepository).should().saveAll(captor.capture());
        List<Plan> saved = new java.util.ArrayList<>();
        captor.getValue().forEach(saved::add);
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Plan::getPlanDate).containsExactly(
                LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11));
        assertThat(saved).extracting(Plan::getSeriesId).doesNotContainNull().hasSameElementsAs(
                List.of(saved.get(0).getSeriesId()));
    }

    @Test
    @DisplayName("deleteSeries - 본인 소유의 같은 반복 묶음만 삭제한다")
    void deleteSeries() {
        Plan mine = plan();
        mine.assignSeries("series-1");
        Plan sameSeriesOther = new Plan("남의 플랜", null, otherAuthor(), PlanCategory.ETC,
                LocalDate.of(2026, 7, 10), null, null);
        sameSeriesOther.assignSeries("series-1");
        given(planRepository.findById(1L)).willReturn(Optional.of(mine));
        given(planRepository.findBySeriesId("series-1")).willReturn(List.of(mine, sameSeriesOther));

        int deleted = planService.deleteSeries(1L, 1L);

        assertThat(deleted).isEqualTo(1);
        then(planRepository).should().deleteAll(List.of(mine));
    }

    @Test
    @DisplayName("deleteSeries - 반복이 아닌 단건 일정이면 그 일정만 삭제한다")
    void deleteSeries_singlePlan() {
        Plan single = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(single));

        int deleted = planService.deleteSeries(1L, 1L);

        assertThat(deleted).isEqualTo(1);
        then(planRepository).should().delete(single);
        then(planRepository).should(never()).findBySeriesId(any());
    }

    @Test
    @DisplayName("update - 폼 값으로 플랜을 수정한다")
    void update() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        PlanForm form = form();
        form.setTitle("알고리즘 공부");
        planService.update(1L, form, 1L);

        assertThat(plan.getTitle()).isEqualTo("알고리즘 공부");
    }

    @Test
    @DisplayName("delete - 플랜을 삭제한다")
    void delete() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.delete(1L, 1L);

        then(planRepository).should().delete(plan);
    }

    @Test
    @DisplayName("다른 회원의 플랜을 수정하려 하면 AccessDeniedException 이 발생한다")
    void update_notOwner() {
        given(planRepository.findById(1L)).willReturn(Optional.of(plan()));

        assertThatThrownBy(() -> planService.update(1L, form(), 999L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("다른 회원의 플랜을 삭제하려 하면 AccessDeniedException 이 발생한다")
    void delete_notOwner() {
        given(planRepository.findById(1L)).willReturn(Optional.of(plan()));

        assertThatThrownBy(() -> planService.delete(1L, 999L))
                .isInstanceOf(AccessDeniedException.class);
        then(planRepository).should(never()).delete(any(Plan.class));
    }

    @Test
    @DisplayName("findById - 없으면 IllegalArgumentException 이 발생한다")
    void findById_notFound() {
        given(planRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> planService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    // ── 조회 범위 ─────────────────────────────────────────────

    @Test
    @DisplayName("findWeek - 어떤 요일을 넘겨도 월~일 범위로 조회한다")
    void findWeek() {
        LocalDate wednesday = LocalDate.of(2026, 7, 8);
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)))
                .willReturn(List.of());

        planService.findWeek(wednesday, 1L);

        then(planRepository).should().findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));
    }

    @Test
    @DisplayName("findMonth - 해당 월의 1일부터 말일까지 조회한다")
    void findMonth() {
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .willReturn(List.of());

        planService.findMonth(YearMonth.of(2026, 7), 1L);

        then(planRepository).should().findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    // ── 상태 변경 ─────────────────────────────────────────────

    @Test
    @DisplayName("toggleCompleted - 완료 상태가 반전된다")
    void toggleCompleted() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.toggleCompleted(1L, 1L);

        assertThat(plan.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("changeShareScope - 새로 공유되면 범위를 실은 PlanSharedEvent 를 발행한다")
    void changeShareScope_publishesEventWithScope() {
        Plan plan = plan();
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.changeShareScope(1L, ShareScope.GROUP, 1L);

        assertThat(plan.isShared()).isTrue();
        ArgumentCaptor<PlanSharedEvent> captor = ArgumentCaptor.forClass(PlanSharedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("자료구조 공부");
        assertThat(captor.getValue().nickname()).isEqualTo("동주");
        assertThat(captor.getValue().authorId()).isEqualTo(1L);
        assertThat(captor.getValue().scope()).isEqualTo(ShareScope.GROUP);
    }

    @Test
    @DisplayName("changeShareScope - 이미 공유된 플랜의 범위 조정은 알리지 않는다 (같은 사람들에게 또 알리면 소음)")
    void changeShareScope_wideningDoesNotPublish() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.GROUP); // 이미 그룹 공유 상태
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.changeShareScope(1L, ShareScope.PUBLIC, 1L);

        assertThat(plan.getShareScope()).isEqualTo(ShareScope.PUBLIC);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("changeShareScope - 비공개로 돌릴 때도 이벤트는 없다")
    void changeShareScope_toPrivateDoesNotPublish() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.PUBLIC);
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.changeShareScope(1L, ShareScope.PRIVATE, 1L);

        assertThat(plan.isShared()).isFalse();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("changeShareScope - 껐다 다시 켜도 두 번째 알림은 없다 (전체 공개 알림은 회원 수만큼 퍼진다)")
    void changeShareScope_reSharingDoesNotPublishAgain() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.PUBLIC);   // 첫 공유 - 이때 이미 알렸다
        plan.changeShareScope(ShareScope.PRIVATE);
        given(planRepository.findById(1L)).willReturn(Optional.of(plan));

        planService.changeShareScope(1L, ShareScope.PUBLIC, 1L);

        assertThat(plan.isShared()).as("알리지 않을 뿐, 공유 자체는 되어야 한다").isTrue();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    // ── 공유 목록 / 열람 자격 ─────────────────────────────────

    @Test
    @DisplayName("findShared - 그룹이 없는 회원에게는 전체 공개만 보인다")
    void findShared_withoutGroupsShowsOnlyPublic() {
        given(studyGroupService.findFellowMemberIds(1L)).willReturn(List.of());
        given(planRepository.findByShareScope(eq(ShareScope.PUBLIC), any()))
                .willReturn(Page.empty());

        planService.findShared(1L, Pageable.unpaged());

        then(planRepository).should().findByShareScope(eq(ShareScope.PUBLIC), any());
        then(planRepository).should(never()).findSharedVisibleTo(any(), any());
    }

    @Test
    @DisplayName("findShared - 그룹이 있으면 같은 그룹 사람들의 그룹 공개까지 함께 조회한다")
    void findShared_withGroupsIncludesFellowGroupPlans() {
        given(studyGroupService.findFellowMemberIds(1L)).willReturn(List.of(1L, 2L, 3L));
        given(planRepository.findSharedVisibleTo(eq(List.of(1L, 2L, 3L)), any()))
                .willReturn(Page.empty());

        planService.findShared(1L, Pageable.unpaged());

        then(planRepository).should().findSharedVisibleTo(eq(List.of(1L, 2L, 3L)), any());
    }

    @Test
    @DisplayName("canView - 그룹 공개 플랜은 같은 그룹일 때만 남이 볼 수 있다")
    void canView_groupScopeRequiresSharedGroup() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.GROUP);

        given(studyGroupService.sharesGroupWith(2L, 1L)).willReturn(true);
        assertThat(planService.canView(plan, 2L)).isTrue();

        given(studyGroupService.sharesGroupWith(3L, 1L)).willReturn(false);
        assertThat(planService.canView(plan, 3L)).isFalse();
    }

    @Test
    @DisplayName("canView - 전체 공개는 그룹 자격을 조회하지 않는다 (대부분의 열람이라 쿼리를 아낀다)")
    void canView_publicSkipsGroupQuery() {
        Plan plan = plan();
        plan.changeShareScope(ShareScope.PUBLIC);

        assertThat(planService.canView(plan, 2L)).isTrue();
        then(studyGroupService).should(never()).sharesGroupWith(any(), any());
    }

    @Test
    @DisplayName("canView - 비공개 플랜은 작성자 본인만 볼 수 있다")
    void canView_privateOnlyForAuthor() {
        Plan plan = plan();

        assertThat(planService.canView(plan, 1L)).isTrue();
        assertThat(planService.canView(plan, 2L)).isFalse();
    }

    // ── 이월 ─────────────────────────────────────────────────

    @Test
    @DisplayName("rollover 는 옮기지 않고 복제한다 - 어제 기록이 뒤에서 바뀌면 안 된다")
    void rollover() {
        // 옮기던 때: 어제 3개 중 1개 완료(33%)였는데 남은 2개를 옮기면
        // 어제에는 완료한 1개만 남아 100% 가 됐다. 통계에 없던 완벽한 하루가 생긴다
        Plan unfinished = plan();                              // plan() 이 만드는 날짜가 7/9 다
        LocalDate yesterday = LocalDate.of(2026, 7, 9);
        LocalDate today = LocalDate.of(2026, 7, 10);
        given(planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(1L, yesterday))
                .willReturn(List.of(unfinished));

        int rolled = planService.rollover(1L, yesterday, today);

        assertThat(rolled).isEqualTo(1);
        // 원본은 어제에 그대로 남는다
        assertThat(unfinished.getPlanDate()).isEqualTo(yesterday);
        // 복제본이 오늘 날짜로 저장된다
        ArgumentCaptor<List<Plan>> saved = ArgumentCaptor.forClass(List.class);
        then(planRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .satisfies(copy -> {
                    assertThat(copy.getPlanDate()).isEqualTo(today);
                    assertThat(copy.getTitle()).isEqualTo(unfinished.getTitle());
                    assertThat(copy.isCompleted()).isFalse();
                });
    }

    // ── 지난주 계획 가져오기 ──────────────────────────────────────
    //
    // 반복 설정(매일·평일·매주)은 등록할 때 미리 정하는 것이다. 한 주를 살아 본 뒤에
    // "이대로 한 주 더" 라고 말하는 자리가 없었다.

    /** 2026-09-07(월)~09-13(일) 이 지난주, 09-14(월)~09-20(일) 이 이번 주 */
    private static final LocalDate LAST_WEEK = LocalDate.of(2026, 9, 7);
    private static final LocalDate THIS_WEEK = LocalDate.of(2026, 9, 14);

    private Plan planOn(LocalDate date, String title) {
        return new Plan(title, null, author(), PlanCategory.MAJOR, date, null, null);
    }

    private void givenWeek(LocalDate weekStart, List<Plan> plans) {
        given(planRepository.findByAuthor_IdAndPlanDateBetweenOrderByPlanDateAscStartTimeAscIdAsc(
                1L, weekStart, weekStart.plusDays(6))).willReturn(plans);
    }

    @SuppressWarnings("unchecked")
    private List<Plan> savedPlans() {
        ArgumentCaptor<List<Plan>> saved = ArgumentCaptor.forClass(List.class);
        then(planRepository).should().saveAll(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("지난주 계획을 이번 주 같은 요일로 옮긴다 - 날짜가 아니라 리듬을 옮기는 일이다")
    void copyWeek_keepsWeekday() {
        givenWeek(LAST_WEEK, List.of(
                planOn(LocalDate.of(2026, 9, 7), "월요일 스터디"),
                planOn(LocalDate.of(2026, 9, 12), "금요일 모의고사")));
        givenWeek(THIS_WEEK, List.of());

        int copied = planService.copyWeek(1L, LAST_WEEK, THIS_WEEK);

        assertThat(copied).isEqualTo(2);
        assertThat(savedPlans())
                .extracting(Plan::getPlanDate, Plan::getTitle)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 14), "월요일 스터디"),
                        tuple(LocalDate.of(2026, 9, 19), "금요일 모의고사"));
    }

    @Test
    @DisplayName("원본은 아무것도 달라지지 않는다 - 지난주 계획을 가져온다고 지난주가 이월된 것은 아니다")
    void copyWeek_leavesSourceUntouched() {
        Plan source = planOn(LocalDate.of(2026, 9, 7), "월요일 스터디");
        givenWeek(LAST_WEEK, List.of(source));
        givenWeek(THIS_WEEK, List.of());

        planService.copyWeek(1L, LAST_WEEK, THIS_WEEK);

        assertThat(source.getPlanDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(source.isRolledOver()).isFalse();
    }

    @Test
    @DisplayName("같은 자리에 같은 제목이 이미 있으면 건너뛴다 - 두 번 눌러도 두 벌이 되지 않게")
    void copyWeek_skipsDuplicates() {
        givenWeek(LAST_WEEK, List.of(
                planOn(LocalDate.of(2026, 9, 7), "월요일 스터디"),
                planOn(LocalDate.of(2026, 9, 8), "화요일 코테")));
        givenWeek(THIS_WEEK, List.of(planOn(LocalDate.of(2026, 9, 14), "월요일 스터디")));

        int copied = planService.copyWeek(1L, LAST_WEEK, THIS_WEEK);

        assertThat(copied).isEqualTo(1);
        assertThat(savedPlans()).singleElement()
                .satisfies(copy -> assertThat(copy.getTitle()).isEqualTo("화요일 코테"));
    }

    @Test
    @DisplayName("가져올 것이 없으면 0 이다 - 실패가 아니라 '없음' 이다")
    void copyWeek_emptySource() {
        givenWeek(LAST_WEEK, List.of());
        givenWeek(THIS_WEEK, List.of());

        assertThat(planService.copyWeek(1L, LAST_WEEK, THIS_WEEK)).isZero();
    }

    @Test
    @DisplayName("같은 주로는 복사할 수 없다 - 스스로를 복사하면 그 주가 두 벌이 된다")
    void copyWeek_rejectsSameWeek() {
        assertThatThrownBy(() -> planService.copyWeek(1L, THIS_WEEK, THIS_WEEK.plusDays(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주 가운데 날짜를 넣어도 그 주 월요일 기준으로 다룬다")
    void copyWeek_normalizesToMonday() {
        givenWeek(LAST_WEEK, List.of(planOn(LocalDate.of(2026, 9, 9), "수요일 인강")));
        givenWeek(THIS_WEEK, List.of());

        planService.copyWeek(1L, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 17));

        assertThat(savedPlans()).singleElement()
                .satisfies(copy -> assertThat(copy.getPlanDate()).isEqualTo(LocalDate.of(2026, 9, 16)));
    }

    @Test
    @DisplayName("이월한 일정은 다시 이월 대상이 되지 않는다 - 두 번 눌러도 두 개가 생기지 않게")
    void rollover_marksSourceAsRolledOver() {
        Plan unfinished = plan();
        LocalDate yesterday = LocalDate.of(2026, 7, 8);
        given(planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(1L, yesterday))
                .willReturn(List.of(unfinished));

        planService.rollover(1L, yesterday, LocalDate.of(2026, 7, 9));

        assertThat(unfinished.isRolledOver()).isTrue();
    }

    @Test
    @DisplayName("복제본은 공유 범위를 물려받지 않는다 - 새 하루의 새 계획이다")
    void rollover_copyIsPrivate() {
        Plan shared = plan();
        shared.changeShareScope(ShareScope.PUBLIC);
        LocalDate yesterday = LocalDate.of(2026, 7, 8);
        given(planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(1L, yesterday))
                .willReturn(List.of(shared));

        planService.rollover(1L, yesterday, LocalDate.of(2026, 7, 9));

        ArgumentCaptor<List<Plan>> saved = ArgumentCaptor.forClass(List.class);
        then(planRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getShareScope()).isEqualTo(ShareScope.PRIVATE);
    }

    @Test
    @DisplayName("rollover 는 옮길 일정이 없으면 0 을 반환한다")
    void rollover_nothingToMove() {
        LocalDate yesterday = LocalDate.of(2026, 7, 8);
        given(planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(1L, yesterday))
                .willReturn(List.of());

        assertThat(planService.rollover(1L, yesterday, LocalDate.of(2026, 7, 9))).isZero();
    }

    @Test
    @DisplayName("findUnfinished 는 그날 남은 일정을 돌려준다 (이월 안내에 쓴다)")
    void findUnfinished() {
        LocalDate date = LocalDate.of(2026, 7, 8);
        given(planRepository.findByAuthor_IdAndPlanDateAndCompletedFalseOrderByStartTimeAscIdAsc(1L, date))
                .willReturn(List.of(plan(), plan()));

        assertThat(planService.findUnfinished(date, 1L)).hasSize(2);
    }
}
