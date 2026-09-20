package com.example.board.group.repository;

import com.example.board.group.domain.Cheer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

    /**
     * 오늘 이 그룹에서 내가 응원한 사람들.
     *
     * <p>그룹 화면은 이 집합으로 버튼을 잠근다. 사람마다 한 번씩 묻지 않고 한 번에 가져오는 이유는,
     * 멤버가 스무 명이면 화면 한 번에 스무 번 묻게 되기 때문이다.</p>
     */
    @Query("""
            select c.recipient.id from Cheer c
            where c.studyGroup.id = :groupId and c.sender.id = :senderId and c.cheerDate = :date
            """)
    List<Long> findRecipientIdsCheeredOn(@Param("groupId") Long groupId,
                                         @Param("senderId") Long senderId,
                                         @Param("date") LocalDate date);

    boolean existsByStudyGroup_IdAndSender_IdAndRecipient_IdAndCheerDate(
            Long groupId, Long senderId, Long recipientId, LocalDate cheerDate);

    /**
     * 그룹을 지우기 전에 응원 기록을 먼저 치운다.
     *
     * <p>DB 에도 {@code on delete cascade} 가 걸려 있지만 거기에 기대지 않는다 -
     * 그 규칙은 마이그레이션에만 있어서, 엔티티로 스키마를 만드는 테스트에는 없다
     * ({@code GroupMemberRepository#deleteByStudyGroup_Id} 와 같은 이유).</p>
     */
    void deleteByStudyGroup_Id(Long groupId);
}
