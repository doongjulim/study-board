package com.example.board.notification.domain;

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

    @Column(nullable = false, length = 255)
    private String message;

    /** 알림 클릭 시 이동할 경로 */
    @Column(length = 100)
    private String url;

    @Column(nullable = false)
    private boolean readFlag;

    @CreatedDate
    private LocalDateTime createdAt;

    public Notification(String message, String url) {
        this.message = message;
        this.url = url;
    }

    public void markRead() {
        this.readFlag = true;
    }
}
