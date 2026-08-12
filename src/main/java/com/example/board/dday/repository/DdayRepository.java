package com.example.board.dday.repository;

import com.example.board.dday.domain.Dday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DdayRepository extends JpaRepository<Dday, Long> {

    /** 내 D-Day 전체 - 목표일이 가까운 순 */
    List<Dday> findByOwner_IdOrderByTargetDateAsc(Long ownerId);

    /** 아직 지나지 않은 D-Day - 플래너 상단 요약에 사용 */
    List<Dday> findByOwner_IdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(Long ownerId, LocalDate from);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByOwner_Id(Long ownerId);
}
