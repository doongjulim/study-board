package com.example.board.group.service;

import com.example.board.group.domain.Cheer;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.event.CheerSentEvent;
import com.example.board.group.repository.CheerRepository;
import com.example.board.group.repository.GroupMemberRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 같은 그룹 사람에게 응원을 보낸다.
 *
 * <p>{@link StudyGroupService} 와 나눠 둔 이유는 다루는 것이 다르기 때문이다 -
 * 저쪽은 그룹의 <b>구성</b>(가입·탈퇴·승계·초대 코드)을 맡고, 이쪽은 그 안에서 오가는 <b>행동</b>을 맡는다.
 * 나중에 응원 말고 다른 반응이 붙는다면 늘어날 곳도 여기다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheerService {

    private final CheerRepository cheerRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    /** "오늘" 은 서비스의 시간대를 따른다 ({@link com.example.board.common.time.ServiceZone}) */
    private final Clock clock;

    /**
     * 응원을 보낸다. 이미 오늘 보냈으면 아무 일도 일어나지 않고 false 를 돌려준다.
     *
     * <p>― 왜 예외가 아니라 false 인가<br>
     * 두 번 누른 것은 <b>사용자의 잘못이 아니다</b> - 화면이 잠기기 전에 손이 빨랐거나,
     * 뒤로 가기로 돌아와 다시 눌렀거나. 결과는 어느 쪽이든 같다("오늘 응원은 보냈다").
     * 오류 화면을 띄우면 성공한 일을 실패로 알리게 된다.
     */
    @Transactional
    public boolean send(Long groupId, Long senderId, Long recipientId) {
        StudyGroup group = findGroupOfBoth(groupId, senderId, recipientId);
        if (senderId.equals(recipientId)) {
            throw new IllegalArgumentException("자기 자신에게는 응원을 보낼 수 없습니다.");
        }

        LocalDate today = LocalDate.now(clock);
        if (cheerRepository.existsByStudyGroup_IdAndSender_IdAndRecipient_IdAndCheerDate(
                groupId, senderId, recipientId, today)) {
            return false;
        }

        Member sender = memberRepository.getReferenceById(senderId);
        Member recipient = memberRepository.getReferenceById(recipientId);
        cheerRepository.save(new Cheer(group, sender, recipient, today));

        eventPublisher.publishEvent(new CheerSentEvent(
                recipientId, findNickname(senderId), group.getName(), groupId));
        return true;
    }

    /** 오늘 내가 이 그룹에서 응원한 사람들 - 화면이 버튼을 잠그는 데 쓴다 */
    public Set<Long> findCheeredToday(Long groupId, Long senderId) {
        return Set.copyOf(cheerRepository.findRecipientIdsCheeredOn(
                groupId, senderId, LocalDate.now(clock)));
    }

    /**
     * 둘 다 이 그룹의 멤버인지 확인한다.
     *
     * <p>보내는 쪽만 확인하면 그룹 밖의 아무에게나 알림을 보낼 수 있는 통로가 된다 -
     * id 를 바꿔 넣는 것은 주소 한 글자를 고치는 일이다.</p>
     */
    private StudyGroup findGroupOfBoth(Long groupId, Long senderId, Long recipientId) {
        List<Long> ids = List.of(senderId, recipientId);
        for (Long memberId : ids) {
            if (!groupMemberRepository.existsByStudyGroup_IdAndMember_Id(groupId, memberId)) {
                throw new AccessDeniedException("같은 그룹의 멤버에게만 응원을 보낼 수 있습니다.");
            }
        }
        return groupMemberRepository.findByStudyGroup_IdAndMember_Id(groupId, senderId)
                .orElseThrow(() -> new AccessDeniedException("같은 그룹의 멤버에게만 응원을 보낼 수 있습니다."))
                .getStudyGroup();
    }

    private String findNickname(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getNickname)
                .orElse("알 수 없음");
    }
}
