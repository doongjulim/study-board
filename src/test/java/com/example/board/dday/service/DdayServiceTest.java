package com.example.board.dday.service;

import com.example.board.dday.domain.Dday;
import com.example.board.dday.dto.DdayForm;
import com.example.board.dday.repository.DdayRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DdayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);
    private static final long OWNER_ID = 1L;

    @Mock DdayRepository ddayRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks DdayService ddayService;

    private Member owner(long id) {
        Member member = new Member("tester" + id, "encoded-password", "테스터" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Dday dday(long ownerId, String title, LocalDate targetDate) {
        return new Dday(owner(ownerId), title, targetDate);
    }

    @Test
    @DisplayName("다가오는 D-Day 는 가까운 순으로 최대 3개까지만 보여준다")
    void findUpcomingLimitsToThree() {
        given(ddayRepository.findByOwner_IdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(
                OWNER_ID, TODAY, PageRequest.ofSize(3)))
                .willReturn(List.of(
                        dday(OWNER_ID, "1", TODAY),
                        dday(OWNER_ID, "2", TODAY.plusDays(1)),
                        dday(OWNER_ID, "3", TODAY.plusDays(2))));

        List<Dday> upcoming = ddayService.findUpcoming(OWNER_ID, TODAY);

        assertThat(upcoming).extracting(Dday::getTitle).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("등록하면 현재 회원이 소유자로 저장된다")
    void create() {
        Member owner = owner(OWNER_ID);
        given(memberRepository.getReferenceById(OWNER_ID)).willReturn(owner);
        given(ddayRepository.save(any(Dday.class))).willAnswer(inv -> inv.getArgument(0));

        DdayForm form = new DdayForm();
        form.setTitle("정보처리기사 실기");
        form.setTargetDate(TODAY.plusDays(30));
        ddayService.create(form, OWNER_ID);

        then(ddayRepository).should().save(argThat(saved ->
                saved.getOwner() == owner && saved.getTitle().equals("정보처리기사 실기")));
    }

    @Test
    @DisplayName("타인의 D-Day 는 조회·수정·삭제할 수 없다")
    void findOwned_notOwner() {
        given(ddayRepository.findById(1L)).willReturn(Optional.of(dday(999L, "남의 D-Day", TODAY)));

        assertThatThrownBy(() -> ddayService.findOwned(1L, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 D-Day 는 예외가 발생한다")
    void findOwned_notFound() {
        given(ddayRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ddayService.findOwned(99L, OWNER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("본인 D-Day 는 수정할 수 있다")
    void update() {
        Dday mine = dday(OWNER_ID, "원래 제목", TODAY);
        given(ddayRepository.findById(1L)).willReturn(Optional.of(mine));

        DdayForm form = new DdayForm();
        form.setTitle("최종 면접");
        form.setTargetDate(TODAY.plusDays(5));
        ddayService.update(1L, form, OWNER_ID);

        assertThat(mine.getTitle()).isEqualTo("최종 면접");
        assertThat(mine.getTargetDate()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    @DisplayName("본인 D-Day 는 삭제할 수 있다")
    void delete() {
        Dday mine = dday(OWNER_ID, "제목", TODAY);
        given(ddayRepository.findById(1L)).willReturn(Optional.of(mine));

        ddayService.delete(1L, OWNER_ID);

        then(ddayRepository).should().delete(mine);
    }
}
