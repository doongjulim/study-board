package com.example.board.group.service;

import com.example.board.group.domain.Cheer;
import com.example.board.group.domain.GroupMember;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.event.CheerSentEvent;
import com.example.board.group.repository.CheerRepository;
import com.example.board.group.repository.GroupMemberRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("그룹 응원")
class CheerServiceTest {

    private static final Long GROUP_ID = 3L;
    private static final Long SENDER = 1L;
    private static final Long RECIPIENT = 2L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Mock CheerRepository cheerRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock MemberRepository memberRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private CheerService cheerService;

    private Member member(Long id, String nickname) {
        Member member = new Member("user" + id, "encoded-password", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @BeforeEach
    void setUp() {
        // 고정 시계 - "오늘" 이 테스트가 도는 날에 따라 달라지면 안 된다
        Clock clock = Clock.fixed(TODAY.atTime(14, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("Asia/Seoul"));
        cheerService = new CheerService(cheerRepository, groupMemberRepository, memberRepository,
                eventPublisher, clock);

        StudyGroup group = new StudyGroup("취준 스터디", null, member(9L, "그룹장"), "ABCD2345");
        ReflectionTestUtils.setField(group, "id", GROUP_ID);

        given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(GROUP_ID, SENDER)).willReturn(true);
        given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(GROUP_ID, RECIPIENT)).willReturn(true);
        given(groupMemberRepository.findByStudyGroup_IdAndMember_Id(GROUP_ID, SENDER))
                .willReturn(Optional.of(new GroupMember(group, member(SENDER, "동주"))));
        given(memberRepository.getReferenceById(SENDER)).willReturn(member(SENDER, "동주"));
        given(memberRepository.getReferenceById(RECIPIENT)).willReturn(member(RECIPIENT, "받는이"));
        given(memberRepository.findById(SENDER)).willReturn(Optional.of(member(SENDER, "동주")));
    }

    @Test
    @DisplayName("응원을 저장하고 받는 사람에게 알릴 이벤트를 발행한다")
    void send() {
        given(cheerRepository.existsByStudyGroup_IdAndSender_IdAndRecipient_IdAndCheerDate(
                GROUP_ID, SENDER, RECIPIENT, TODAY)).willReturn(false);

        assertThat(cheerService.send(GROUP_ID, SENDER, RECIPIENT)).isTrue();

        ArgumentCaptor<Cheer> saved = ArgumentCaptor.forClass(Cheer.class);
        then(cheerRepository).should().save(saved.capture());
        assertThat(saved.getValue().getCheerDate()).isEqualTo(TODAY);

        ArgumentCaptor<CheerSentEvent> event = ArgumentCaptor.forClass(CheerSentEvent.class);
        then(eventPublisher).should().publishEvent(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(RECIPIENT);
        assertThat(event.getValue().senderNickname()).isEqualTo("동주");
        // 여러 그룹에 속한 사람에게는 "누가" 만큼이나 "어디서" 가 궁금하다
        assertThat(event.getValue().groupName()).isEqualTo("취준 스터디");
    }

    @Test
    @DisplayName("오늘 이미 보냈으면 아무 일도 일어나지 않는다 - 두 번 누른 것은 사용자의 잘못이 아니다")
    void alreadySentToday() {
        given(cheerRepository.existsByStudyGroup_IdAndSender_IdAndRecipient_IdAndCheerDate(
                GROUP_ID, SENDER, RECIPIENT, TODAY)).willReturn(true);

        assertThat(cheerService.send(GROUP_ID, SENDER, RECIPIENT)).isFalse();

        then(cheerRepository).should(never()).save(any());
        // 저장하지 않았으면 알림도 없어야 한다 - 열 번 받은 응원은 한 번보다 반갑지 않다
        then(eventPublisher).should(never()).publishEvent(any(CheerSentEvent.class));
    }

    @Test
    @DisplayName("자기 자신에게는 보낼 수 없다")
    void cannotCheerSelf() {
        assertThatThrownBy(() -> cheerService.send(GROUP_ID, SENDER, SENDER))
                .isInstanceOf(IllegalArgumentException.class);

        then(cheerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("받는 사람이 그 그룹의 멤버가 아니면 막는다 - 보내는 쪽만 보면 그룹 밖 아무에게나 알림을 보낼 수 있다")
    void recipientMustBeInTheSameGroup() {
        given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(GROUP_ID, 99L)).willReturn(false);

        assertThatThrownBy(() -> cheerService.send(GROUP_ID, SENDER, 99L))
                .isInstanceOf(AccessDeniedException.class);

        then(cheerRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("보내는 사람이 그 그룹의 멤버가 아니어도 막는다")
    void senderMustBeInTheGroup() {
        given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(GROUP_ID, 98L)).willReturn(false);

        assertThatThrownBy(() -> cheerService.send(GROUP_ID, 98L, RECIPIENT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("오늘 응원한 사람들을 한 번에 가져온다 - 멤버가 스무 명이면 스무 번 묻게 된다")
    void findCheeredToday() {
        given(cheerRepository.findRecipientIdsCheeredOn(GROUP_ID, SENDER, TODAY))
                .willReturn(List.of(2L, 5L));

        assertThat(cheerService.findCheeredToday(GROUP_ID, SENDER)).containsExactlyInAnyOrder(2L, 5L);
    }
}
