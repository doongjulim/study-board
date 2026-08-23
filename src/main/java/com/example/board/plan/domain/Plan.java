package com.example.board.plan.domain;

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
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCategory category;

    @Column(nullable = false)
    private LocalDate planDate;

    private LocalTime startTime; // null 이면 종일 일정

    private LocalTime endTime;

    @Column(nullable = false)
    private boolean completed;

    /** 누구까지 볼 수 있는가 - 판단 규칙은 {@link ShareScope} 에 있다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShareScope shareScope = ShareScope.PRIVATE;

    /** 공유 소식을 이미 알렸는지. 공유는 몇 번이든 껐다 켤 수 있지만 소식은 한 번뿐이다 */
    @Column(nullable = false)
    private boolean shareNotified;

    @Column(nullable = false)
    private boolean reminderSent;

    /** 반복 생성된 일정들을 묶는 식별자 - 단건 일정은 null */
    @Column(length = 36)
    private String seriesId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Plan(String title, String content, Member author, PlanCategory category,
                LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = (category != null) ? category : PlanCategory.ETC;
        this.planDate = planDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** 현재 사용자가 이 플랜의 작성자인지 확인한다 */
    public boolean isAuthoredBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    /** 같은 반복 묶음에 속하게 한다 */
    public void assignSeries(String seriesId) {
        this.seriesId = seriesId;
    }

    public boolean isPartOfSeries() {
        return seriesId != null;
    }

    /** 시작 시각(날짜+시간). 시간이 없는 종일 일정은 없다 */
    public LocalDateTime startsAt() {
        if (startTime == null) {
            throw new IllegalStateException("종일 일정에는 시작 시각이 없습니다.");
        }
        return planDate.atTime(startTime);
    }

    /** 계획된 공부 시간(분). 종일 일정처럼 시간이 없으면 0분으로 본다 */
    public long getStudyMinutes() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    public void update(String title, String content, PlanCategory category,
                       LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        this.title = title;
        this.content = content;
        this.category = (category != null) ? category : PlanCategory.ETC;
        this.planDate = planDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderSent = false; // 일정이 바뀌면 리마인더를 다시 받을 수 있어야 한다
    }

    public void toggleCompleted() {
        this.completed = !this.completed;
    }

    /**
     * 날짜만 옮긴다 (어제 못 한 일정을 오늘로 가져올 때).
     *
     * <p>이미 끝낸 일정을 옮기면 지난 기록이 바뀌어 통계가 흔들리므로 막는다.
     * 날짜가 바뀌면 리마인더를 다시 받을 수 있어야 하고,
     * 반복 묶음에서 떼어 낸 것이므로 시리즈에서도 빠진다.</p>
     */
    public void moveTo(LocalDate date) {
        if (completed) {
            throw new IllegalStateException("이미 완료한 일정은 옮길 수 없습니다.");
        }
        this.planDate = date;
        this.reminderSent = false;
        this.seriesId = null;
    }

    public boolean isShared() {
        return shareScope.isShared();
    }

    /**
     * 공유 범위를 바꾸고, 이 플랜을 <b>처음 공유하는</b> 경우에만 true 를 반환한다 (알림 발행 조건).
     *
     * <p>이미 공유된 플랜의 범위 조정(GROUP↔PUBLIC)은 알리지 않는다 —
     * 그룹에 알림이 간 플랜을 전체 공개로 넓혔다고 같은 사람들에게 또 알리면 소음이다.</p>
     *
     * <p>"처음" 이었는지를 플랜에 남겨 두는 이유는, 직전 상태만 보고 판단하면 공개↔비공개를
     * 오갈 때마다 새 공유로 읽히기 때문이다. 전체 공개 알림은 회원 수만큼 퍼지므로,
     * 버튼을 껐다 켜는 것만으로 알림을 무한히 찍어낼 수 있게 된다.
     * 공유는 몇 번이든 껐다 켜도 되지만 <b>소식은 한 번</b>이다.</p>
     */
    public boolean changeShareScope(ShareScope newScope) {
        ShareScope target = (newScope != null) ? newScope : ShareScope.PRIVATE;
        boolean firstShare = !shareNotified && !this.shareScope.isShared() && target.isShared();
        this.shareScope = target;
        if (firstShare) {
            this.shareNotified = true;
        }
        return firstShare;
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 빠를 수 없습니다.");
        }
    }
}
