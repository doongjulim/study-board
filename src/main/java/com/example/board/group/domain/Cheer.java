package com.example.board.group.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 같은 그룹 사람에게 보내는 응원 한 번.
 *
 * <p>― 왜 필요한가<br>
 * 그룹에는 이번 주 순위와 챌린지 달성만 있었다. <b>순위는 상위권을 더 뛰게 하지만
 * 하위권을 조용히 떠나게 한다.</b> 이 서비스를 쓰는 사람은 이미 스스로를 충분히 다그치고 있고,
 * 그룹이 그 압력을 한 겹 더하기만 하면 남을 이유가 없다.
 * 응원은 순위와 반대 방향으로 작동한다 - 누구에게나 보낼 수 있고, 받는 쪽에 손해가 없다.
 *
 * <p>― 왜 하루에 한 번인가<br>
 * 제한이 없으면 버튼을 연타해 알림을 무한히 찍어낼 수 있다. 공유 알림에서 이미 겪은 문제다
 * ({@code plan.share_notified}). 게다가 열 번 받은 응원은 한 번 받은 응원보다 반갑지 않다 -
 * 흔해지면 뜻이 없어진다. DB 유니크 제약(V33)과 서비스가 함께 막는다.
 *
 * <p>날짜를 {@code LocalDate} 로 따로 들고 있는 이유는 "오늘 이미 보냈는가" 가 <b>시각이 아니라
 * 날짜</b>의 문제이기 때문이다. 타임스탬프로 판단하면 자정 경계에서 규칙이 흔들린다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cheer", uniqueConstraints = @UniqueConstraint(
        name = "uq_cheer_daily", columnNames = {"group_id", "sender_id", "recipient_id", "cheer_date"}))
public class Cheer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 필드명을 group 으로 두지 않는다 - JPQL 에서 group 은 group by 의 키워드다 (GroupMember 와 같은 이유) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup studyGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(nullable = false)
    private LocalDate cheerDate;

    @CreatedDate
    private LocalDateTime createdAt;

    public Cheer(StudyGroup studyGroup, Member sender, Member recipient, LocalDate cheerDate) {
        if (sender.getId() != null && sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("자기 자신에게는 응원을 보낼 수 없습니다.");
        }
        this.studyGroup = studyGroup;
        this.sender = sender;
        this.recipient = recipient;
        this.cheerDate = cheerDate;
    }
}
