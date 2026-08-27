package com.example.board.dday.service;

import com.example.board.dday.domain.Dday;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.repository.DdayRepository;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DdayService {

    /** 플래너 상단에 함께 보여줄 최대 개수 */
    private static final int UPCOMING_LIMIT = 3;

    private final DdayRepository ddayRepository;
    private final MemberRepository memberRepository;

    public List<Dday> findMine(Long memberId) {
        return ddayRepository.findByOwner_IdOrderByTargetDateAsc(memberId);
    }

    /** 아직 지나지 않은 가까운 D-Day 몇 개 - 플래너 요약용 */
    public List<Dday> findUpcoming(Long memberId, LocalDate today) {
        return ddayRepository.findByOwner_IdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(
                memberId, today, PageRequest.ofSize(UPCOMING_LIMIT));
    }

    public Dday findOwned(Long id, Long memberId) {
        Dday dday = ddayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("D-Day 가 존재하지 않습니다. id=" + id));
        if (!dday.isOwnedBy(memberId)) {
            throw new AccessDeniedException("본인의 D-Day 만 수정하거나 삭제할 수 있습니다.");
        }
        return dday;
    }

    @Transactional
    public Long create(DdayForm form, Long memberId) {
        Dday dday = new Dday(memberRepository.getReferenceById(memberId),
                form.getTitle(), form.getTargetDate());
        return ddayRepository.save(dday).getId();
    }

    @Transactional
    public void update(Long id, DdayForm form, Long memberId) {
        findOwned(id, memberId).update(form.getTitle(), form.getTargetDate());
    }

    @Transactional
    public void delete(Long id, Long memberId) {
        ddayRepository.delete(findOwned(id, memberId));
    }
}
