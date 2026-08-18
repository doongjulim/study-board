package com.example.board.retro.service;

import com.example.board.member.repository.MemberRepository;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.domain.Retrospective;
import com.example.board.retro.repository.RetrospectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

    private final RetrospectiveRepository retrospectiveRepository;
    private final MemberRepository memberRepository;

    /**
     * 회고를 쓰거나 고친다.
     *
     * <p>같은 날짜에 이미 있으면 새로 만들지 않고 내용을 바꾼다 - 그날의 생각은 하나면 되고,
     * "오늘 회고를 이미 썼던가?" 를 사용자가 기억하지 않아도 되게 한다.
     * 날짜 정규화를 조회 전에 해 두지 않으면, 수요일에 두 번 쓴 주간 회고가 두 줄로 남는다.</p>
     */
    @Transactional
    public Retrospective write(Long memberId, RetroType type, LocalDate date, String content) {
        LocalDate anchor = type.anchorDate(date);
        return find(memberId, type, anchor)
                .map(existing -> {
                    existing.edit(content);
                    return existing;
                })
                .orElseGet(() -> retrospectiveRepository.save(Retrospective.write(
                        memberRepository.getReferenceById(memberId), type, anchor, content)));
    }

    /** 회고를 지운다 - 남기고 싶지 않은 날도 있다 */
    @Transactional
    public void delete(Long id, Long memberId) {
        Retrospective retro = retrospectiveRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회고가 존재하지 않습니다. id=" + id));
        if (!retro.isOwnedBy(memberId)) {
            throw new AccessDeniedException("본인의 회고만 삭제할 수 있습니다.");
        }
        retrospectiveRepository.delete(retro);
    }

    public Optional<Retrospective> find(Long memberId, RetroType type, LocalDate date) {
        return retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(
                memberId, type, type.anchorDate(date));
    }

    /** 기간의 하루 회고들 (오래된 날부터) */
    public List<Retrospective> findDailies(Long memberId, LocalDate from, LocalDate to) {
        return retrospectiveRepository
                .findByOwner_IdAndTypeAndTargetDateBetweenOrderByTargetDateAsc(
                        memberId, RetroType.DAILY, from, to);
    }

    /** 회원 탈퇴 시 개인 회고를 지운다 */
    @Transactional
    public void deleteAllOf(Long memberId) {
        retrospectiveRepository.deleteByOwner_Id(memberId);
    }
}
