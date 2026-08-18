package com.example.board.retro.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회고 - 그날(그 주) 무엇을 느꼈는지 남기는 한 줄.
 *
 * <p>숫자만 쌓이는 통계는 "왜 그랬는지" 를 말해 주지 않는다.
 * 완료율이 40% 인 주가 아팠던 주인지 계획을 과하게 잡은 주인지는 본인만 알고,
 * 그걸 적어 두지 않으면 다음 주에 같은 실수를 반복한다.</p>
 *
 * <p>같은 날짜에 두 개가 생기지 않도록 (owner, type, targetDate) 를 유니크로 묶는다.
 * 회고는 새로 쌓는 게 아니라 그날 것을 고쳐 쓰는 것이기 때문이다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "retrospective", uniqueConstraints =
        @UniqueConstraint(name = "uq_retro_owner_type_date",
                columnNames = {"owner_id", "type", "target_date"}))
public class Retrospective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RetroType type;

    /** 회고가 매달린 날짜. 주간은 그 주 월요일로 맞춰 저장된다 */
    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 2000)
    private String content;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * 회고를 쓴다. 날짜 정규화와 길이 판정은 {@link RetroType} 이 맡으므로
     * 어느 경로로 들어와도 같은 규칙이 걸린다.
     */
    public static Retrospective write(Member owner, RetroType type, LocalDate date, String content) {
        Retrospective retro = new Retrospective();
        retro.owner = owner;
        retro.type = type;
        retro.targetDate = type.anchorDate(date);
        retro.content = validated(type, content);
        return retro;
    }

    /** 회고는 쌓지 않고 고쳐 쓴다 - 그날의 생각은 하나면 된다 */
    public void edit(String content) {
        this.content = validated(type, content);
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getId().equals(memberId);
    }

    private static String validated(RetroType type, String content) {
        if (!type.fits(content)) {
            throw new IllegalArgumentException(
                    "%s는 1자 이상 %d자 이하로 입력하세요.".formatted(type.getLabel(), type.getMaxLength()));
        }
        return content.strip();
    }
}
