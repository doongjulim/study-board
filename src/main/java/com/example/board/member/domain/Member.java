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

    /** 매일 30분 - 처음 쓰는 사람이 부담 없이 이어 갈 수 있는 기본값 */
    public static final int DEFAULT_DAILY_GOAL_MINUTES = 30;

    /** 하루는 1440분이므로 그 이상은 달성할 수 없는 목표다 */
    private static final int MAX_DAILY_GOAL_MINUTES = 1440;

    /** 탈퇴 후에도 로그인할 수 없도록 넣는 값. BCrypt 형식이 아니라 어떤 비밀번호와도 일치하지 않는다 */
    private static final String UNUSABLE_PASSWORD = "!withdrawn";

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

    /** 비밀번호 찾기용. 선택 입력이므로 nullable 이다 */
    @Column(unique = true, length = 100)
    private String email;

    /**
     * 하루 목표 학습 시간(분). 연속 달성일(스트릭) 판정 기준이 된다.
     * 0 으로 두면 시간 조건 없이 계획 완료만으로 판정한다.
     */
    @Column(nullable = false)
    private int dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES;

    /** 탈퇴 시각. null 이면 활성 회원이다 (행을 지우지 않고 익명화만 한다) */
    private LocalDateTime withdrawnAt;

    /** 첫 사용 안내를 마친 시각. null 이면 아직 안 봤다 */
    private LocalDateTime onboardedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public Member(String loginId, String encodedPassword, String nickname) {
        this(loginId, encodedPassword, nickname, null);
    }

    public Member(String loginId, String encodedPassword, String nickname, String email) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.email = blankToNull(email);
        this.dailyGoalMinutes = DEFAULT_DAILY_GOAL_MINUTES;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeEmail(String email) {
        this.email = blankToNull(email);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeDailyGoal(int minutes) {
        if (minutes < 0 || minutes > MAX_DAILY_GOAL_MINUTES) {
            throw new IllegalArgumentException("하루 목표 시간은 0분 이상 %d분 이하여야 합니다."
                    .formatted(MAX_DAILY_GOAL_MINUTES));
        }
        this.dailyGoalMinutes = minutes;
    }

    /**
     * 탈퇴 처리. 행을 지우는 대신 개인 정보를 지우고 익명 이름을 남긴다.
     *
     * <p>회원 행을 실제로 삭제하면 그 사람이 남긴 게시글·댓글이 함께 사라져
     * 다른 사람의 스레드에 구멍이 생긴다. 글은 남기되 누구인지는 알 수 없게 한다.</p>
     *
     * <p>아이디·닉네임에 id 를 붙이는 것은 유니크 제약 때문이다.
     * 동시에 원래 쓰던 아이디가 풀려 다시 가입할 수 있게 된다.</p>
     */
    public void withdraw(LocalDateTime at) {
        if (isWithdrawn()) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }
        this.withdrawnAt = at;
        this.loginId = "withdrawn_" + id;
        this.nickname = "탈퇴한 회원" + id;
        this.password = UNUSABLE_PASSWORD;
        this.email = null;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /** 첫 사용 안내를 마쳤다고 기록한다 (건너뛰기도 마친 것으로 본다 - 다시 붙잡지 않는다) */
    public void completeOnboarding(LocalDateTime at) {
        if (onboardedAt == null) {
            this.onboardedAt = at;
        }
    }

    public boolean isOnboarded() {
        return onboardedAt != null;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
