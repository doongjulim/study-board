package com.example.board.dday.domain;

import com.example.board.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 시험·면접 등 목표일까지 남은 날짜를 세는 D-Day */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Dday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false)
    private LocalDate targetDate;

    @CreatedDate
    private LocalDateTime createdAt;

    public Dday(Member owner, String title, LocalDate targetDate) {
        this.owner = owner;
        this.title = title;
        this.targetDate = targetDate;
    }

    public void update(String title, LocalDate targetDate) {
        this.title = title;
        this.targetDate = targetDate;
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getId().equals(memberId);
    }

    /** 남은 일수. 오늘이면 0, 지난 목표는 음수 */
    public long remainingDays(LocalDate today) {
        return ChronoUnit.DAYS.between(today, targetDate);
    }

    /** 화면 표기용 문자열 (D-7 / D-DAY / D+3) */
    public String label(LocalDate today) {
        long remaining = remainingDays(today);
        if (remaining == 0) {
            return "D-DAY";
        }
        return remaining > 0 ? "D-" + remaining : "D+" + Math.abs(remaining);
    }

    public boolean isPast(LocalDate today) {
        return remainingDays(today) < 0;
    }
}
