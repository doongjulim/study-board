package com.example.board.notification.repository;

import com.example.board.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 벨 패널에 띄우는 최근 몇 건 - 전체 목록은 findByRecipient_IdOrderByIdDesc 를 쓴다 */
    List<Notification> findTop10ByRecipient_IdOrderByIdDesc(Long recipientId);

    /** 전체 보기 화면 - 10건에서 끊기지 않도록 페이지로 준다 */
    Page<Notification> findByRecipient_IdOrderByIdDesc(Long recipientId, Pageable pageable);

    /**
     * 남의 알림을 건드리지 못하게, 조회 단계에서 수신자까지 함께 건다.
     * 찾은 뒤에 소유자를 확인하면 "있다/없다" 가 먼저 새어 나간다.
     */
    Optional<Notification> findByIdAndRecipient_Id(Long id, Long recipientId);

    List<Notification> findByRecipient_IdAndReadFlagFalse(Long recipientId);

    /** SSE 재연결 시 놓친 알림 - 마지막으로 받은 id 이후의 것 */
    List<Notification> findByRecipient_IdAndIdGreaterThanOrderByIdAsc(Long recipientId, Long lastId);

    long countByRecipient_IdAndReadFlagFalse(Long recipientId);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByRecipient_Id(Long recipientId);

    /**
     * 오래된 알림 정리.
     *
     * <p>읽은 것은 짧게, 안 읽은 것은 길게 둔다 - 안 읽었다는 건 아직 볼 생각이 있다는 뜻이지만,
     * 그마저도 반년이 지나면 알림으로서의 쓸모가 없고 남은 건 행뿐이다.
     * 두 규칙을 한 문장에 두는 이유는 보존 정책이 두 곳으로 갈라지지 않게 하기 위해서다.</p>
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            delete from Notification n
            where (n.readFlag = true and n.createdAt < :readBefore)
               or n.createdAt < :anyBefore
            """)
    int deleteOld(@Param("readBefore") LocalDateTime readBefore,
                  @Param("anyBefore") LocalDateTime anyBefore);
}
