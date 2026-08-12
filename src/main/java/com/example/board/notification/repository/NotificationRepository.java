package com.example.board.notification.repository;

import com.example.board.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByRecipient_IdOrderByIdDesc(Long recipientId);

    List<Notification> findByRecipient_IdAndReadFlagFalse(Long recipientId);

    /** SSE 재연결 시 놓친 알림 - 마지막으로 받은 id 이후의 것 */
    List<Notification> findByRecipient_IdAndIdGreaterThanOrderByIdAsc(Long recipientId, Long lastId);

    long countByRecipient_IdAndReadFlagFalse(Long recipientId);

    /** 회원 탈퇴 시 개인 데이터 정리 */
    void deleteByRecipient_Id(Long recipientId);
}
