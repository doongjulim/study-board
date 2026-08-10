package com.example.board.member.domain;

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
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String loginId;

    /** BCrypt 해시 - 원문 비밀번호는 절대 저장하지 않는다 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    /**
     * 하루 목표 학습 시간(분). 연속 달성일(스트릭) 판정 기준이 된다.
     * 0 으로 두면 시간 조건 없이 계획 완료만으로 판정한다.
     */
    @Column(nullable = false)
    private int dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES;

    @CreatedDate
    private LocalDateTime createdAt;

    /** 매일 30분 - 처음 쓰는 사람이 부담 없이 이어 갈 수 있는 기본값 */
    public static final int DEFAULT_DAILY_GOAL_MINUTES = 30;

    /** 하루는 1440분이므로 그 이상은 달성할 수 없는 목표다 */
    private static final int MAX_DAILY_GOAL_MINUTES = 1440;

    public Member(String loginId, String encodedPassword, String nickname) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES;
    }

    public void changeDailyGoal(int minutes) {
        if (minutes < 0 || minutes > MAX_DAILY_GOAL_MINUTES) {
            throw new IllegalArgumentException("하루 목표 시간은 0분 이상 %d분 이하여야 합니다."
                    .formatted(MAX_DAILY_GOAL_MINUTES));
        }
        this.dailyGoalMinutes = minutes;
    }
}
