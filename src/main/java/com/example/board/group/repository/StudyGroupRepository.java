package com.example.board.group.repository;

import com.example.board.group.domain.StudyGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * open-in-view 가 꺼져 있으므로 화면에 노출되는 조회는
 * {@link EntityGraph} 로 owner 를 함께 로딩한다 (그룹장 표시에 쓴다).
 */
public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<StudyGroup> findById(Long id);

    @EntityGraph(attributePaths = "owner")
    Optional<StudyGroup> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
