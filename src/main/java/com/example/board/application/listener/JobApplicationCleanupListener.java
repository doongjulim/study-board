package com.example.board.application.listener;

import com.example.board.application.repository.JobApplicationRepository;
import com.example.board.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 지원 기록을 정리한다.
 *
 * <p>글·댓글과 달리 남기지 않는다 - 남의 대화가 끊기는 것이 아니라 <b>순전히 개인의 기록</b>이고,
 * 어디에 지원했는지는 가장 남기고 싶지 않은 종류의 정보다.</p>
 */
@Component
@RequiredArgsConstructor
public class JobApplicationCleanupListener {

    private final JobApplicationRepository jobApplicationRepository;

    @EventListener
    @Transactional
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        jobApplicationRepository.deleteByOwner_Id(event.memberId());
    }
}
