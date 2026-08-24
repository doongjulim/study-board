package com.example.board.member.service;

import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.service.DdayService;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.service.StudyGroupService;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.notification.domain.NotificationType;
import com.example.board.notification.service.NotificationService;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.dto.PlanForm;
import com.example.board.plan.service.PlanService;
import com.example.board.post.dto.PostForm;
import com.example.board.post.service.PostService;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.service.RetrospectiveService;
import com.example.board.session.service.StudySessionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * 탈퇴 한 번이 일곱 모듈을 지나는 길을 <b>실제로</b> 걸어 본다.
 *
 * <p>정리 작업은 {@code MemberWithdrawnEvent} 를 각 모듈이 받아 스스로 처리한다. 발행하는 쪽은
 * 누가 듣는지 모르고, 듣는 쪽은 서로를 모른다 - 모듈을 떼어 놓는 대가로, <b>아무도 전체를 보지 않는다.</b>
 * 리스너 하나가 빠지거나 {@code @Order} 가 어긋나도 예외는 나지 않는다. 조용히 남은 데이터가
 * 있을 뿐이고, 그걸 알아차릴 방법이 지금까지 없었다.</p>
 *
 * <p>단위 테스트로는 대신할 수 없다. 각 리스너를 따로 검증하면 "리스너가 있다면 지운다" 만 확인된다.
 * 여기서 확인하는 것은 "탈퇴하면 실제로 다 지워진다" 이고, 그 둘을 가르는 것이 등록 누락과 순서다.</p>
 *
 * <p>되돌릴 수 없는 삭제 경로라 사후 발견이 곧 데이터 유실이다. 그래서 느린 통합 테스트 값을 치른다.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("회원 탈퇴 정리")
class MemberWithdrawalIntegrationTest {

    private static final String PASSWORD = "password123!";

    @Autowired MemberService memberService;
    @Autowired PlanService planService;
    @Autowired StudySessionService sessionService;
    @Autowired DdayService ddayService;
    @Autowired RetrospectiveService retrospectiveService;
    @Autowired NotificationService notificationService;
    @Autowired StudyGroupService studyGroupService;
    @Autowired PostService postService;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager em;

    private Member leaver;
    private Member bystander;

    @BeforeEach
    void setUp() {
        leaver = createMember("leaver", "떠나는사람");
        bystander = createMember("bystander", "남는사람");
    }

    private Member createMember(String loginId, String nickname) {
        return memberRepository.save(
                new Member(loginId, passwordEncoder.encode(PASSWORD), nickname));
    }

    /** 이 회원의 학습 데이터를 한 벌 만든다 (모듈마다 하나씩) */
    private void createStudyDataFor(Member member) throws IOException {
        Long planId = planService.create(planForm("자료구조 공부"), member.getId());

        // 학습 기록은 계획을 참조한다 - 정리 순서가 어긋나면 여기가 고아가 된다
        Long sessionId = sessionService
                .start(member.getId(), planId, PlanCategory.MAJOR, LocalDateTime.of(2026, 8, 20, 9, 0))
                .getId();
        sessionService.stop(sessionId, member.getId(), LocalDateTime.of(2026, 8, 20, 10, 0));

        ddayService.create(ddayForm(), member.getId());
        retrospectiveService.write(member.getId(), RetroType.DAILY,
                LocalDate.of(2026, 8, 20), "집중이 잘 됐다");
        notificationService.notify(member.getId(), NotificationType.COMMENT, "댓글이 달렸어요", "/posts/1");

        Long postId = postService.create(postForm(), member.getId());
        postService.toggleLike(postId, member.getId());
    }

    private PlanForm planForm(String title) {
        PlanForm form = new PlanForm();
        form.setTitle(title);
        form.setCategory(PlanCategory.MAJOR);
        form.setPlanDate(LocalDate.of(2026, 8, 20));
        return form;
    }

    private DdayForm ddayForm() {
        DdayForm form = new DdayForm();
        form.setTitle("정보처리기사");
        form.setTargetDate(LocalDate.of(2026, 12, 1));
        return form;
    }

    private PostForm postForm() {
        PostForm form = new PostForm();
        form.setTitle("공부 기록");
        form.setContent("오늘도 했다");
        return form;
    }

    /** 벌크 삭제는 영속성 컨텍스트를 지나치므로, 남은 것을 보려면 DB 에 직접 물어야 한다 */
    private long countOwned(String entity, String ownerField, Member owner) {
        em.flush();
        em.clear();
        return em.createQuery(
                        "select count(e) from " + entity + " e where e." + ownerField + ".id = :id", Long.class)
                .setParameter("id", owner.getId())
                .getSingleResult();
    }

    private void withdraw(Member member) {
        em.flush();
        memberService.withdraw(member.getId(), PASSWORD);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("탈퇴하면 개인 학습 데이터가 모듈마다 하나도 남지 않는다 - 리스너 하나만 빠져도 조용히 남는다")
    void removesEveryPersonalRecord() throws IOException {
        createStudyDataFor(leaver);

        withdraw(leaver);

        assertThat(countOwned("StudySession", "owner", leaver)).as("학습 기록").isZero();
        assertThat(countOwned("Plan", "author", leaver)).as("계획").isZero();
        assertThat(countOwned("Dday", "owner", leaver)).as("D-Day").isZero();
        assertThat(countOwned("Retrospective", "owner", leaver)).as("회고").isZero();
        assertThat(countOwned("Notification", "recipient", leaver)).as("알림").isZero();
        assertThat(countOwned("PostLike", "member", leaver)).as("좋아요").isZero();
    }

    @Test
    @DisplayName("탈퇴하면 캘린더 구독 주소가 끊긴다 - 주소의 토큰이 곧 열쇠라 남겨 두면 계획이 계속 흘러나간다")
    void cutsOffCalendarSubscription() {
        leaver.issueCalendarToken("subscription-token");

        withdraw(leaver);

        // 이 주소는 로그인 없이 열린다. 탈퇴 후에도 살아 있으면 구글 캘린더가 계속 읽어 간다
        assertThat(memberRepository.findById(leaver.getId()).orElseThrow().hasCalendarToken())
                .isFalse();
    }

    @Test
    @DisplayName("남는 회원의 기록은 건드리지 않는다 - 정리 범위가 넓으면 남의 데이터가 사라진다")
    void keepsOtherMembersRecords() throws IOException {
        createStudyDataFor(leaver);
        createStudyDataFor(bystander);

        withdraw(leaver);

        assertThat(countOwned("StudySession", "owner", bystander)).isEqualTo(1);
        assertThat(countOwned("Plan", "author", bystander)).isEqualTo(1);
        assertThat(countOwned("Dday", "owner", bystander)).isEqualTo(1);
        assertThat(countOwned("Retrospective", "owner", bystander)).isEqualTo(1);
        assertThat(countOwned("Notification", "recipient", bystander)).isEqualTo(1);
        assertThat(countOwned("PostLike", "member", bystander)).isEqualTo(1);
    }

    @Test
    @DisplayName("남긴 글은 그대로 두고 누구인지만 지운다 - 지우면 남의 스레드에 구멍이 생긴다")
    void keepsPostsButAnonymizesAuthor() throws IOException {
        Long postId = postService.create(postForm(), leaver.getId());

        withdraw(leaver);

        assertThat(postService.findById(postId).getTitle()).isEqualTo("공부 기록");
        Member withdrawn = memberRepository.findById(leaver.getId()).orElseThrow();
        assertThat(withdrawn.getNickname()).startsWith("탈퇴한 회원");
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("혼자 쓰던 그룹은 함께 사라진다 - 아무도 못 들어가는 그룹이 목록에 남지 않게")
    void deletesGroupLeftAlone() {
        studyGroupService.create(groupForm(), leaver.getId());

        withdraw(leaver);

        assertThat(countAll("StudyGroup")).isZero();
        assertThat(countAll("GroupMember")).isZero();
    }

    @Test
    @DisplayName("남은 사람이 있는 그룹은 가장 오래된 멤버가 이어받는다 - 그룹장이 나갔다고 스터디가 없어지면 안 된다")
    void handsOverGroupToOldestMember() {
        Long groupId = studyGroupService.create(groupForm(), leaver.getId());
        String inviteCode = studyGroupService.findGroupForMember(groupId, leaver.getId()).getInviteCode();
        studyGroupService.join(inviteCode, bystander.getId());

        withdraw(leaver);

        assertThat(studyGroupService.findGroupForMember(groupId, bystander.getId()).getOwner().getId())
                .isEqualTo(bystander.getId());
        assertThat(countOwned("GroupMember", "member", leaver)).isZero();
    }

    private GroupForm groupForm() {
        GroupForm form = new GroupForm();
        form.setName("코테 스터디");
        form.setDescription("매주 3문제");
        return form;
    }

    private long countAll(String entity) {
        em.flush();
        em.clear();
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }
}
