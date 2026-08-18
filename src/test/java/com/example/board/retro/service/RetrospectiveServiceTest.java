package com.example.board.retro.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.retro.domain.RetroType;
import com.example.board.retro.domain.Retrospective;
import com.example.board.retro.repository.RetrospectiveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RetrospectiveServiceTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);

    @Mock RetrospectiveRepository retrospectiveRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks RetrospectiveService retrospectiveService;

    private Member owner(Long id) {
        Member member = new Member("tester" + id, "encoded-password", "동주" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Retrospective existing(String content) {
        return Retrospective.write(owner(1L), RetroType.DAILY, WEDNESDAY, content);
    }

    @Test
    @DisplayName("처음 쓰면 새로 저장한다")
    void createsWhenAbsent() {
        given(retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(1L, RetroType.DAILY, WEDNESDAY))
                .willReturn(Optional.empty());
        given(memberRepository.getReferenceById(1L)).willReturn(owner(1L));
        given(retrospectiveRepository.save(any(Retrospective.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Retrospective saved = retrospectiveService.write(1L, RetroType.DAILY, WEDNESDAY, "집중 잘 됨");

        assertThat(saved.getContent()).isEqualTo("집중 잘 됨");
        then(retrospectiveRepository).should().save(any(Retrospective.class));
    }

    @Test
    @DisplayName("이미 쓴 날이면 새로 쌓지 않고 고쳐 쓴다 - 그날의 생각은 하나면 된다")
    void editsWhenPresent() {
        Retrospective already = existing("처음 생각");
        given(retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(1L, RetroType.DAILY, WEDNESDAY))
                .willReturn(Optional.of(already));

        retrospectiveService.write(1L, RetroType.DAILY, WEDNESDAY, "다시 생각");

        assertThat(already.getContent()).isEqualTo("다시 생각");
        then(retrospectiveRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("주간 회고는 조회 전에 월요일로 맞춘다 - 안 그러면 한 주에 일곱 개가 생긴다")
    void weeklyLooksUpByMonday() {
        given(retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(1L, RetroType.WEEKLY, MONDAY))
                .willReturn(Optional.empty());
        given(memberRepository.getReferenceById(1L)).willReturn(owner(1L));
        given(retrospectiveRepository.save(any(Retrospective.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        retrospectiveService.write(1L, RetroType.WEEKLY, WEDNESDAY, "계획을 과하게 잡았다");

        then(retrospectiveRepository).should()
                .findByOwner_IdAndTypeAndTargetDate(1L, RetroType.WEEKLY, MONDAY);
    }

    @Test
    @DisplayName("조회도 같은 정규화를 거친다")
    void findNormalizesDate() {
        given(retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(1L, RetroType.WEEKLY, MONDAY))
                .willReturn(Optional.empty());

        retrospectiveService.find(1L, RetroType.WEEKLY, WEDNESDAY);

        then(retrospectiveRepository).should()
                .findByOwner_IdAndTypeAndTargetDate(1L, RetroType.WEEKLY, MONDAY);
    }

    @Test
    @DisplayName("남의 회고는 지울 수 없다")
    void onlyOwnerCanDelete() {
        given(retrospectiveRepository.findById(5L)).willReturn(Optional.of(existing("내 회고")));

        assertThatThrownBy(() -> retrospectiveService.delete(5L, 999L))
                .isInstanceOf(AccessDeniedException.class);
        then(retrospectiveRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("길이 규칙을 어기면 저장되지 않는다")
    void rejectsTooLongContent() {
        given(retrospectiveRepository.findByOwner_IdAndTypeAndTargetDate(eq(1L), eq(RetroType.DAILY), any()))
                .willReturn(Optional.empty());
        given(memberRepository.getReferenceById(1L)).willReturn(owner(1L));

        assertThatThrownBy(() ->
                retrospectiveService.write(1L, RetroType.DAILY, WEDNESDAY, "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
        then(retrospectiveRepository).should(never()).save(any());
    }
}
