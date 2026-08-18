package com.example.board.group.service;

import com.example.board.group.domain.GroupMember;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.repository.GroupMemberRepository;
import com.example.board.group.repository.StudyGroupRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class StudyGroupServiceTest {

    @Mock StudyGroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks StudyGroupService studyGroupService;

    private Member member(Long id, String loginId) {
        Member member = new Member(loginId, "encoded-password", loginId + "-닉");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private StudyGroup group(Long id, Member owner) {
        StudyGroup group = new StudyGroup("코테 스터디", "매일 두 문제", owner, "ABCD2345");
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }

    private GroupMember membership(StudyGroup group, Member member) {
        return new GroupMember(group, member);
    }

    @Nested
    @DisplayName("만들기")
    class Create {

        @Test
        @DisplayName("만든 사람이 그룹장이자 첫 멤버가 된다")
        void creatorBecomesOwnerAndFirstMember() {
            Member owner = member(1L, "leader");
            given(memberRepository.getReferenceById(1L)).willReturn(owner);
            given(groupRepository.existsByInviteCode(anyString())).willReturn(false);
            given(groupRepository.save(any(StudyGroup.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            GroupForm form = new GroupForm();
            form.setName("코테 스터디");
            studyGroupService.create(form, 1L);

            ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
            then(groupMemberRepository).should().save(captor.capture());
            assertThat(captor.getValue().getMember()).isSameAs(owner);
            assertThat(captor.getValue().getStudyGroup().isOwnedBy(1L)).isTrue();
        }

        @Test
        @DisplayName("초대 코드가 이미 있으면 겹치지 않을 때까지 다시 뽑는다")
        void regeneratesInviteCodeOnCollision() {
            given(memberRepository.getReferenceById(1L)).willReturn(member(1L, "leader"));
            given(groupRepository.existsByInviteCode(anyString())).willReturn(true, false);
            given(groupRepository.save(any(StudyGroup.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            GroupForm form = new GroupForm();
            form.setName("코테 스터디");
            studyGroupService.create(form, 1L);

            then(groupRepository).should(times(2)).existsByInviteCode(anyString());
        }

        @Test
        @DisplayName("가입 상한을 넘기면 만들 수 없다 - 무한 가입은 그룹 공개를 사실상 전체 공개로 만든다")
        void rejectsWhenAtGroupLimit() {
            given(groupMemberRepository.countByMember_Id(1L))
                    .willReturn((long) StudyGroupService.MAX_GROUPS_PER_MEMBER);

            GroupForm form = new GroupForm();
            form.setName("하나 더");

            assertThatThrownBy(() -> studyGroupService.create(form, 1L))
                    .isInstanceOf(IllegalStateException.class);
            then(groupRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("초대 코드 가입")
    class Join {

        @Test
        @DisplayName("소문자·공백이 섞여 있어도 코드로 인정한다 (말로 전해 듣고 적는 값이다)")
        void normalizesInviteCode() {
            Member owner = member(1L, "leader");
            StudyGroup group = group(10L, owner);
            given(groupRepository.findByInviteCode("ABCD2345")).willReturn(Optional.of(group));
            given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(10L, 2L)).willReturn(false);
            given(memberRepository.getReferenceById(2L)).willReturn(member(2L, "joiner"));

            StudyGroup joined = studyGroupService.join("  abcd2345 ", 2L);

            assertThat(joined).isSameAs(group);
            then(groupMemberRepository).should().save(any(GroupMember.class));
        }

        @Test
        @DisplayName("없는 코드는 그룹의 존재를 노출하지 않는 한 가지 문구로만 거절한다")
        void rejectsUnknownCode() {
            given(groupRepository.findByInviteCode("WRONG234")).willReturn(Optional.empty());

            assertThatThrownBy(() -> studyGroupService.join("WRONG234", 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("초대 코드가 올바르지 않습니다");
        }

        @Test
        @DisplayName("이미 가입한 그룹에는 다시 가입할 수 없다")
        void rejectsDuplicateJoin() {
            StudyGroup group = group(10L, member(1L, "leader"));
            given(groupRepository.findByInviteCode("ABCD2345")).willReturn(Optional.of(group));
            given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(10L, 2L)).willReturn(true);

            assertThatThrownBy(() -> studyGroupService.join("ABCD2345", 2L))
                    .isInstanceOf(IllegalStateException.class);
            then(groupMemberRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("일반 멤버가 나가면 소속만 정리된다")
        void memberLeavesQuietly() {
            Member owner = member(1L, "leader");
            Member fellow = member(2L, "fellow");
            StudyGroup group = group(10L, owner);
            GroupMember membership = membership(group, fellow);
            given(groupMemberRepository.findByStudyGroup_IdAndMember_Id(10L, 2L))
                    .willReturn(Optional.of(membership));

            studyGroupService.leave(10L, 2L);

            then(groupMemberRepository).should().delete(membership);
            then(groupRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("그룹장이 나가면 가장 오래된 멤버가 그룹장을 이어받는다")
        void ownerLeavingTransfersOwnership() {
            Member owner = member(1L, "leader");
            Member oldest = member(2L, "oldest");
            Member latest = member(3L, "latest");
            StudyGroup group = group(10L, owner);
            GroupMember ownerMembership = membership(group, owner);
            given(groupMemberRepository.findByStudyGroup_IdAndMember_Id(10L, 1L))
                    .willReturn(Optional.of(ownerMembership));
            given(groupMemberRepository.findByStudyGroup_IdOrderByJoinedAtAscIdAsc(10L))
                    .willReturn(List.of(ownerMembership, membership(group, oldest), membership(group, latest)));

            studyGroupService.leave(10L, 1L);

            assertThat(group.isOwnedBy(2L)).isTrue();
            then(groupMemberRepository).should().delete(ownerMembership);
            then(groupRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("혼자 남은 그룹장이 나가면 그룹째 없어진다 - 주인 없는 그룹을 남기지 않는다")
        void lastOwnerLeavingDeletesGroup() {
            Member owner = member(1L, "leader");
            StudyGroup group = group(10L, owner);
            GroupMember ownerMembership = membership(group, owner);
            given(groupMemberRepository.findByStudyGroup_IdAndMember_Id(10L, 1L))
                    .willReturn(Optional.of(ownerMembership));
            given(groupMemberRepository.findByStudyGroup_IdOrderByJoinedAtAscIdAsc(10L))
                    .willReturn(List.of(ownerMembership));

            studyGroupService.leave(10L, 1L);

            then(groupRepository).should().delete(group);
        }
    }

    @Test
    @DisplayName("그룹 삭제는 그룹장만 할 수 있다")
    void onlyOwnerCanDelete() {
        StudyGroup group = group(10L, member(1L, "leader"));
        given(groupRepository.findById(10L)).willReturn(Optional.of(group));

        assertThatThrownBy(() -> studyGroupService.delete(10L, 2L))
                .isInstanceOf(AccessDeniedException.class);
        then(groupRepository).should(never()).delete(any(StudyGroup.class));
    }

    @Test
    @DisplayName("그룹 상세는 멤버만 볼 수 있다 - 초대 코드가 화면에 실리기 때문이다")
    void detailRequiresMembership() {
        given(groupMemberRepository.existsByStudyGroup_IdAndMember_Id(10L, 9L)).willReturn(false);

        assertThatThrownBy(() -> studyGroupService.findGroupForMember(10L, 9L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("회원 탈퇴 시 모든 소속에 나가기와 같은 승계 규칙이 적용된다")
    void leaveAllAppliesSameSuccessionRule() {
        Member withdrawing = member(1L, "leaving");
        StudyGroup owned = group(10L, withdrawing);
        GroupMember ownedMembership = membership(owned, withdrawing);
        given(groupMemberRepository.findByMember_IdOrderByIdAsc(1L))
                .willReturn(List.of(ownedMembership));
        given(groupMemberRepository.findByStudyGroup_IdAndMember_Id(10L, 1L))
                .willReturn(Optional.of(ownedMembership));
        given(groupMemberRepository.findByStudyGroup_IdOrderByJoinedAtAscIdAsc(10L))
                .willReturn(List.of(ownedMembership));

        studyGroupService.leaveAll(1L);

        then(groupRepository).should().delete(owned);
    }
}
