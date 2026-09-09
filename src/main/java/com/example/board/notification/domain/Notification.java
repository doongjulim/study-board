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

    /** 메시지 상한. 컬럼 길이와 같은 값이어야 하므로 한 곳에서 선언하고 둘 다 이것을 쓴다 */
    public static final int MAX_MESSAGE_LENGTH = 255;
    private static final String ELLIPSIS = "\u2026";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 알림을 받는 회원 - 알림은 수신자 본인에게만 보인다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
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
        this.message = abbreviate(message);
        this.url = url;
    }

    /**
     * 컬럼 상한을 넘는 메시지를 잘라 낸다.
     *
     * <p>여기서 자르는 이유: 메시지를 만드는 곳이 넷이고(댓글·답글·공유·리마인더), 각자가 재료의
     * 길이를 알아야 한다면 언젠가 하나가 빠진다. 실제로 그랬다 - 답글 알림만 '제목' 자리에
     * 원댓글 <b>본문</b>(500자)을 넣어서, 긴 댓글에 답글을 달면 커밋 때 컬럼 상한에 걸렸다.
     * 알림은 같은 트랜잭션에서 저장되므로 <b>답글까지 함께 롤백됐다</b>.</p>
     *
     * <p>길이 규칙은 컬럼이 정한다. 그러니 컬럼과 같은 자리에 둔다 -
     * 부르는 쪽은 무엇을 넣든 저장에 실패하지 않는다.</p>
     */
    public static String abbreviate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH - 1) + ELLIPSIS;
    }

    public void markRead() {
        this.readFlag = true;
    }
}
