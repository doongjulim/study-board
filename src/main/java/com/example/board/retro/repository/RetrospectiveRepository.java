package com.example.board.retro.repository;

import com.example.board.retro.domain.RetroType;
import com.example.board.retro.domain.Retrospective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RetrospectiveRepository extends JpaRepository<Retrospective, Long> {

    /** 그날(그 주) 회고 - 새로 쌓지 않고 고쳐 쓰기 위해 먼저 찾는다 */
    Optional<Retrospective> findByOwner_IdAndTypeAndTargetDate(
            Long ownerId, RetroType type, LocalDate targetDate);

    /** 기간의 하루 회고들 - 주간 인증글 초안과 돌아보기 화면에 쓴다 */
    List<Retrospective> findByOwner_IdAndTypeAndTargetDateBetweenOrderByTargetDateAsc(
            Long ownerId, RetroType type, LocalDate from, LocalDate to);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByOwner_Id(Long ownerId);
}
