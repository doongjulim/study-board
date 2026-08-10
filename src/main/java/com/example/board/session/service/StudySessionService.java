package com.example.board.session.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.repository.PlanRepository;
import com.example.board.session.domain.StudySession;
import com.example.board.session.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudySessionService {

    private final StudySessionRepository sessionRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;

    /** 지금 진행 중인 내 세션 (헤더 타이머가 매 요청 확인한다) */
    public Optional<StudySession> findRunning(Long memberId) {
        return sessionRepository.findByOwner_IdAndEndedAtIsNull(memberId);
    }

    /** 기간별 내 학습 기록 - 통계 집계용 */
    public List<StudySession> findBetween(Long memberId, LocalDate from, LocalDate to) {
        return sessionRepository.findByOwner_IdAndStudyDateBetweenOrderByStartedAtAsc(memberId, from, to);
    }

    /**
     * 학습을 시작한다.
     * 진행 중인 세션이 있으면 시작할 수 없다 - 동시에 두 가지를 공부한 것으로 집계되면
     * 합계가 실제 시간을 넘어서기 때문이다.
     */
    @Transactional
    public StudySession start(Long memberId, Long planId, PlanCategory category, LocalDateTime now) {
        if (findRunning(memberId).isPresent()) {
            throw new IllegalStateException("이미 진행 중인 학습이 있습니다. 먼저 종료해 주세요.");
        }
        Member owner = memberRepository.getReferenceById(memberId);
        return sessionRepository.save(StudySession.start(owner, findOwnedPlan(planId, memberId), category, now));
    }

    @Transactional
    public StudySession stop(Long sessionId, Long memberId, LocalDateTime now) {
        StudySession session = findOwned(sessionId, memberId);
        session.stop(now);
        return session;
    }

    /** 타이머를 깜빡했을 때 실제 구간으로 보정한다 */
    @Transactional
    public StudySession adjust(Long sessionId, Long memberId, LocalDateTime startedAt, LocalDateTime endedAt) {
        StudySession session = findOwned(sessionId, memberId);
        session.adjust(startedAt, endedAt);
        return session;
    }

    @Transactional
    public void delete(Long sessionId, Long memberId) {
        sessionRepository.delete(findOwned(sessionId, memberId));
    }

    /**
     * 켜둔 채 방치된 세션을 닫는다. 종료한 건수를 반환한다.
     * 사용자가 직접 누른 종료와 구분되도록 방치 표시를 남겨, 나중에 보정할 수 있게 한다.
     */
    @Transactional
    public int closeAbandoned(LocalDateTime now) {
        List<StudySession> stale = sessionRepository.findByEndedAtIsNullAndStartedAtBefore(
                now.minus(StudySession.MAX_DURATION));
        stale.forEach(StudySession::closeAsAbandoned);
        return stale.size();
    }

    public StudySession findOwned(Long sessionId, Long memberId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 기록이 존재하지 않습니다. id=" + sessionId));
        if (!session.isOwnedBy(memberId)) {
            throw new AccessDeniedException("본인의 학습 기록만 다룰 수 있습니다.");
        }
        return session;
    }

    /** 계획과 함께 시작하는 경우에만 조회한다. 남의 계획으로는 시작할 수 없다 */
    private Plan findOwnedPlan(Long planId, Long memberId) {
        if (planId == null) {
            return null;
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("플랜이 존재하지 않습니다. id=" + planId));
        if (!plan.isAuthoredBy(memberId)) {
            throw new AccessDeniedException("본인의 플랜으로만 학습을 시작할 수 있습니다.");
        }
        return plan;
    }
}
