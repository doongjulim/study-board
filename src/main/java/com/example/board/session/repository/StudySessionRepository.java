package com.example.board.session.repository;

import com.example.board.session.domain.StudySession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면·응답에 노출되는 조회는
 * {@link EntityGraph} 로 연관 엔티티를 함께 로딩한다.
 */
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    @Override
    @EntityGraph(attributePaths = {"owner", "plan"})
    Optional<StudySession> findById(Long id);

    /** 회원이 지금 진행 중인 세션 (DB 유니크 인덱스로 최대 1건이 보장된다) */
    @EntityGraph(attributePaths = {"owner", "plan"})
    Optional<StudySession> findByOwner_IdAndEndedAtIsNull(Long ownerId);

    /** 기간별 학습 기록 - 통계 집계에 사용 */
    @EntityGraph(attributePaths = "plan")
    List<StudySession> findByOwner_IdAndStudyDateBetweenOrderByStartedAtAsc(
            Long ownerId, LocalDate from, LocalDate to);

    /** 오래 켜둔 채 방치된 세션 - 자동 종료 대상 */
    List<StudySession> findByEndedAtIsNullAndStartedAtBefore(LocalDateTime threshold);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByOwner_Id(Long ownerId);
}
