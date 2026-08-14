package com.example.board.group.service;

import com.example.board.group.domain.GroupMember;
import com.example.board.group.domain.InviteCode;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.repository.GroupMemberRepository;
import com.example.board.group.repository.StudyGroupRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyGroupService {

    /** 취준 스터디는 두어 개가 현실적이다 - 무한 가입은 그룹 공개 범위를 사실상 전체 공개로 만든다 */
    static final int MAX_GROUPS_PER_MEMBER = 10;

    /** 유니크 제약과 충돌하면 다시 뽑는다. 코드 공간이 커서 이 횟수를 넘길 확률은 사실상 0 이다 */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final StudyGroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;

    private final Random random = new SecureRandom();

    /** 그룹을 만들고 만든 사람을 그룹장이자 첫 멤버로 넣는다 */
    @Transactional
    public Long create(GroupForm form, Long memberId) {
        validateGroupLimit(memberId);
        Member owner = memberRepository.getReferenceById(memberId);
        StudyGroup group = groupRepository.save(
                new StudyGroup(form.getName(), form.getDescription(), owner, newUniqueInviteCode()));
        groupMemberRepository.save(new GroupMember(group, owner));
        return group.getId();
    }

    /**
     * 초대 코드로 가입한다. 코드는 대소문자 없이 적어도 통하도록 대문자로 맞춘다.
     * 없는 코드는 "잘못된 코드" 라고만 알려 그룹의 존재를 노출하지 않는다.
     */
    @Transactional
    public StudyGroup join(String inviteCode, Long memberId) {
        String normalized = (inviteCode == null) ? "" : inviteCode.trim().toUpperCase();
        StudyGroup group = groupRepository.findByInviteCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("초대 코드가 올바르지 않습니다."));
        if (groupMemberRepository.existsByStudyGroup_IdAndMember_Id(group.getId(), memberId)) {
            throw new IllegalStateException("이미 가입한 그룹입니다.");
        }
        validateGroupLimit(memberId);
        groupMemberRepository.save(new GroupMember(group, memberRepository.getReferenceById(memberId)));
        return group;
    }

    /**
     * 그룹에서 나간다. 그룹장이 나가면 가장 오래된 멤버가 이어받고,
     * 혼자 남은 그룹장이 나가면 그룹을 없앤다 (주인 없는 그룹을 남기지 않는다).
     */
    @Transactional
    public void leave(Long groupId, Long memberId) {
        GroupMember membership = groupMemberRepository.findByStudyGroup_IdAndMember_Id(groupId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("가입하지 않은 그룹입니다."));
        StudyGroup group = membership.getStudyGroup();

        if (!group.isOwnedBy(memberId)) {
            groupMemberRepository.delete(membership);
            return;
        }

        List<GroupMember> successors = groupMemberRepository
                .findByStudyGroup_IdOrderByJoinedAtAscIdAsc(groupId).stream()
                .filter(candidate -> !candidate.getMember().getId().equals(memberId))
                .toList();
        if (successors.isEmpty()) {
            groupRepository.delete(group); // 소속 행은 on delete cascade 로 함께 정리된다
            return;
        }
        group.transferOwnershipTo(successors.get(0).getMember());
        groupMemberRepository.delete(membership);
    }

    /** 그룹 삭제는 그룹장만 할 수 있다 */
    @Transactional
    public void delete(Long groupId, Long memberId) {
        StudyGroup group = findById(groupId);
        if (!group.isOwnedBy(memberId)) {
            throw new AccessDeniedException("그룹장만 그룹을 삭제할 수 있습니다.");
        }
        groupRepository.delete(group);
    }

    /** 탈퇴한 회원의 모든 소속을 정리한다 - 그룹마다 나가기와 같은 승계 규칙을 적용한다 */
    @Transactional
    public void leaveAll(Long memberId) {
        groupMemberRepository.findByMember_IdOrderByIdAsc(memberId)
                .forEach(membership -> leave(membership.getStudyGroup().getId(), memberId));
    }

    public List<StudyGroup> findMyGroups(Long memberId) {
        return groupMemberRepository.findByMember_IdOrderByIdAsc(memberId).stream()
                .map(GroupMember::getStudyGroup)
                .toList();
    }

    /** 그룹 상세는 멤버만 볼 수 있다 - 초대 코드가 화면에 실리기 때문이다 */
    public StudyGroup findGroupForMember(Long groupId, Long memberId) {
        if (!groupMemberRepository.existsByStudyGroup_IdAndMember_Id(groupId, memberId)) {
            throw new AccessDeniedException("그룹 멤버만 볼 수 있습니다.");
        }
        return findById(groupId);
    }

    public List<GroupMember> findMembers(Long groupId) {
        return groupMemberRepository.findByStudyGroup_IdOrderByJoinedAtAscIdAsc(groupId);
    }

    /** 나와 같은 그룹에 속한 회원 id (나 포함). 그룹 공개 플랜의 노출·알림 대상 */
    public List<Long> findFellowMemberIds(Long memberId) {
        return groupMemberRepository.findFellowMemberIds(memberId);
    }

    /** 그룹 공개 플랜을 볼 자격 - 작성자와 같은 그룹에 속해 있는가 */
    public boolean sharesGroupWith(Long viewerId, Long authorId) {
        return groupMemberRepository.countSharedGroups(viewerId, authorId) > 0;
    }

    private StudyGroup findById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("그룹이 존재하지 않습니다. id=" + groupId));
    }

    private void validateGroupLimit(Long memberId) {
        if (groupMemberRepository.countByMember_Id(memberId) >= MAX_GROUPS_PER_MEMBER) {
            throw new IllegalStateException(
                    "그룹은 최대 %d개까지 가입할 수 있습니다.".formatted(MAX_GROUPS_PER_MEMBER));
        }
    }

    private String newUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = InviteCode.generate(random);
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다. 다시 시도해 주세요.");
    }
}
