package com.example.board.session.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 실제로 공부한 구간 기록.
 *
 * <p>{@code Plan} 이 "하기로 한 시간"이라면 이쪽은 "실제로 앉아 있던 시간"이다.
 * 통계가 계획 시간이 아니라 이 기록을 합산해야 숫자를 신뢰할 수 있다.</p>
 *
 * <p>계획 없이도 기록할 수 있도록 {@link #plan} 은 nullable 이며,
 * 소유자를 따로 들고 있어 계획이 지워져도 학습 기록은 남는다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class StudySession {

    /**
     * 방치된 세션으로 판단하는 기준 길이.
     * 사용자가 직접 종료한 세션에는 적용하지 않는다 (실제로 길게 공부했을 수 있으므로).
     */
    public static final Duration MAX_DURATION = Duration.ofHours(6);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    /** 계획 없이 시작한 공부는 null. 계획이 삭제되어도 기록은 남는다 (on delete set null) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCategory category;

    /** 집계 기준일 - 시작 시각의 날짜(자정 기준)로 고정한다 */
    @Column(nullable = false)
    private LocalDate studyDate;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** null 이면 아직 진행 중 */
    private LocalDateTime endedAt;

    /** 켜둔 채 방치되어 자동 종료된 기록 - 사용자에게 보정을 유도하는 표시 */
    @Column(nullable = false)
    private boolean abandoned;

    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * 학습을 시작한다.
     * 계획과 함께 시작하면 분류는 계획을 따르고, 계획이 없으면 지정한 분류(없으면 기타)를 쓴다.
     */
    public static StudySession start(Member owner, Plan plan, PlanCategory category, LocalDateTime startedAt) {
        StudySession session = new StudySession();
        session.owner = owner;
        session.plan = plan;
        session.category = resolveCategory(plan, category);
        session.startedAt = startedAt;
        session.studyDate = startedAt.toLocalDate();
        return session;
    }

    /** 사용자가 직접 종료한다. 길이 제한을 두지 않고 실제 구간을 그대로 인정한다 */
    public void stop(LocalDateTime endedAt) {
        requireRunning();
        validateRange(startedAt, endedAt);
        this.endedAt = endedAt;
    }

    /** 켜둔 채 방치된 세션을 최대 길이까지만 인정하고 닫는다 (스케줄러 전용) */
    public void closeAsAbandoned() {
        requireRunning();
        this.endedAt = startedAt.plus(MAX_DURATION);
        this.abandoned = true;
    }

    /** 타이머를 깜빡했거나 잘못 눌렀을 때 실제 구간으로 고쳐 준다 */
    public void adjust(LocalDateTime startedAt, LocalDateTime endedAt) {
        validateRange(startedAt, endedAt);
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.studyDate = startedAt.toLocalDate();
        this.abandoned = false; // 사용자가 직접 확인했으므로 방치 표시를 뗀다
    }

    public boolean isRunning() {
        return endedAt == null;
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getId().equals(memberId);
    }

    /** 기록된 학습 시간(분). 진행 중이면 아직 확정되지 않았으므로 0 */
    public long minutes() {
        return isRunning() ? 0 : Duration.between(startedAt, endedAt).toMinutes();
    }

    /** 화면 타이머 표시용 경과 초. 진행 중이면 기준 시각까지, 종료됐으면 기록된 구간 */
    public long elapsedSeconds(LocalDateTime now) {
        return Duration.between(startedAt, isRunning() ? now : endedAt).toSeconds();
    }

    /** 계획과 연결된 기록인지 - 계획 삭제 시 null 이 될 수 있어 화면에서 확인이 필요하다 */
    public boolean hasPlan() {
        return plan != null;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("이미 종료된 학습 기록입니다.");
        }
    }

    private static void validateRange(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null) {
            throw new IllegalArgumentException("시작·종료 시각이 모두 필요합니다.");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private static PlanCategory resolveCategory(Plan plan, PlanCategory category) {
        if (plan != null) {
            return plan.getCategory();
        }
        return (category != null) ? category : PlanCategory.ETC;
    }
}
