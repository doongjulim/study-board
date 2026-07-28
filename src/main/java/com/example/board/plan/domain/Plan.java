package com.example.board.plan.domain;

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

    @Column(nullable = false, length = 50)
    private String writer;

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

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Plan(String title, String content, String writer,
                LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        this.title = title;
        this.content = content;
        this.writer = writer;
        this.planDate = planDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void update(String title, String content,
                       LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        this.title = title;
        this.content = content;
        this.planDate = planDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderSent = false; // 일정이 바뀌면 리마인더를 다시 받을 수 있어야 한다
    }

    public void toggleCompleted() {
        this.completed = !this.completed;
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
