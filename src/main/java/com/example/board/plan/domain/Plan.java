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

    @Column(nullable = false)
    private boolean shared;

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

    /** 공유 상태를 전환하고, 새로 공유된 경우에만 true 를 반환한다 */
    public boolean toggleShared() {
        this.shared = !this.shared;
        return this.shared;
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
