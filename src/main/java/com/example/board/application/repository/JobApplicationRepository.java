package com.example.board.application.repository;

import com.example.board.application.domain.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /**
     * 내 지원 목록.
     *
     * <p>진행 중인 것이 먼저, 그 안에서는 마감이 가까운 순서다. 끝난 지원은 아래로 내려간다 -
     * 목록을 여는 이유가 "다음에 뭘 해야 하나" 이기 때문이다.
     * 마감일이 없는 것은 진행 중이라도 뒤로 보낸다({@code nulls last}) - NULL 의 자리는 표준이
     * 정하지 않아 H2 와 PostgreSQL 이 다르게 두므로 직접 적는다.</p>
     */
    @Query("""
            select a from JobApplication a
            where a.owner.id = :ownerId
            order by case when a.result = com.example.board.application.domain.ApplicationResult.IN_PROGRESS
                          then 0 else 1 end,
                     a.deadline asc nulls last,
                     a.id desc
            """)
    List<JobApplication> findMine(@Param("ownerId") Long ownerId);

    /**
     * 곧 마감인 진행 중 지원 - 홈·일간 뷰의 안내에 쓴다.
     *
     * <p>지난 마감도 포함한다. 넘겼다는 사실 자체가 알아야 할 정보이기 때문이다.</p>
     */
    @Query("""
            select a from JobApplication a
            where a.owner.id = :ownerId
              and a.result = com.example.board.application.domain.ApplicationResult.IN_PROGRESS
              and a.deadline is not null and a.deadline <= :until
            order by a.deadline asc, a.id asc
            """)
    List<JobApplication> findUpcoming(@Param("ownerId") Long ownerId, @Param("until") LocalDate until);

    /** 탈퇴 정리 - 지원 기록은 개인 것이라 남기지 않는다 */
    void deleteByOwner_Id(Long ownerId);
}
