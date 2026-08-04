package com.example.board.notification.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 알림을 받는 회원 - 알림은 수신자 본인에게만 보인다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(nullable = false, length = 255)
    private String message;

    /** 알림 클릭 시 이동할 경로 */
    @Column(length = 100)
    private String url;

    @Column(nullable = false)
    private boolean readFlag;

    @CreatedDate
    private LocalDateTime createdAt;

    public Notification(Member recipient, String message, String url) {
        this.recipient = recipient;
        this.message = message;
        this.url = url;
    }

    public void markRead() {
        this.readFlag = true;
    }
}
