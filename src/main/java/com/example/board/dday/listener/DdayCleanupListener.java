package com.example.board.dday.listener;

import com.example.board.dday.repository.DdayRepository;
import com.example.board.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴한 회원의 D-Day 를 정리한다 */
@Component
@RequiredArgsConstructor
public class DdayCleanupListener {

    private final DdayRepository ddayRepository;

    @EventListener
    @Transactional
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        ddayRepository.deleteByOwner_Id(event.memberId());
    }
}
