package com.example.board.plan.domain;

import com.example.board.common.time.ReadableDuration;
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

    /** 예상 소요 시간의 상한(분) - 하루. 근거는 {@link #validateEstimate} 에 적었다 */
    public static final int MAX_ESTIMATED_MINUTES = 24 * 60;

    /**
     * 제목·메모의 길이 상한. <b>컬럼 길이와 같은 값이어야 하므로 여기 한 번만 적고</b>
     * 컬럼 선언과 폼 검증이 모두 이것을 쓴다.
     *
     * <p>밖에서 들어오는 값(캘린더 가져오기)은 우리 폼을 거치지 않으므로 검증이 걸리지 않는다.
     * 알림 메시지에서 이미 한 번 겪은 일이다 - 컬럼 상한을 넘는 값이 커밋 때 터지면서
     * 같은 트랜잭션의 다른 저장까지 함께 롤백됐다({@code Notification#abbreviate}).</p>
     */
    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_CONTENT_LENGTH)
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

    /**
     * 예상 소요 시간(분). 적지 않았으면 null.
     *
     * <p>― 왜 시작·종료 시각과 따로 두는가<br>
     * 통계의 '계획 대비 실행률' 은 한동안 <b>대부분의 사용자에게 보이지 않았다.</b>
     * 계획 시간을 {@code endTime - startTime} 으로만 구했는데, 정작 주 입력 경로인
     * 한 줄 추가는 시각을 받지 않기 때문이다. 시각이 없으면 계획 시간이 0이고,
     * 0이면 화면이 실행률을 조용히 감췄다 - 기능이 없어진 줄도 모르고 쓰게 된다.
     *
     * <p>"언제 할까"(시각)와 "얼마나 걸릴까"(소요 시간)는 다른 값이다.
     * 사람은 대개 뒤의 것을 먼저 안다 - 몇 시에 앉을지는 몰라도 두 시간쯤 걸린다는 건 안다.
     * 그리고 실행률이 답하려던 질문("내가 계획을 과하게 잡는구나")에 필요한 것도 뒤의 값이다.
     *
     * <p>시각이 둘 다 있으면 그것이 더 정확한 정보이므로 그쪽을 쓴다({@link #getStudyMinutes}).
     */
    private Integer estimatedMinutes;

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

    /**
     * 이 계획을 다음 날로 이월했는가.
     *
     * <p>이월이 '옮기기' 에서 '복제' 로 바뀌면서 필요해졌다 - 원본이 어제에 그대로 남으므로,
     * 표시가 없으면 버튼을 두 번 누른 사람에게 오늘 같은 계획이 두 개 생긴다.
     * "이월했다" 는 것도 그날 있었던 일이므로 기록으로 남길 값이다.</p>
     */
    @Column(nullable = false)
    private boolean rolledOver;

    /** 반복 생성된 일정들을 묶는 식별자 - 단건 일정은 null */
    @Column(length = 36)
    private String seriesId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Plan(String title, String content, Member author, PlanCategory category,
                LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        this(title, content, author, category, planDate, startTime, endTime, null);
    }

    public Plan(String title, String content, Member author, PlanCategory category,
                LocalDate planDate, LocalTime startTime, LocalTime endTime,
                Integer estimatedMinutes) {
        validateTimeRange(startTime, endTime);
        validateEstimate(estimatedMinutes);
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = (category != null) ? category : PlanCategory.ETC;
        this.planDate = planDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.estimatedMinutes = estimatedMinutes;
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

    /**
     * 계획된 공부 시간(분).
     *
     * <p>시작·종료 시각이 둘 다 있으면 그 구간이 가장 정확한 정보이므로 그것을 쓰고,
     * 없으면 직접 적은 예상 소요 시간을 쓴다. 둘 다 없으면 0 이다.</p>
     */
    public long getStudyMinutes() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMinutes();
        }
        return estimatedMinutes != null ? estimatedMinutes : 0;
    }

    /** 계획 시간을 적어 두었는가 - 화면이 '계획 대비' 를 보여 줄지 정할 때 쓴다 */
    public boolean hasPlannedTime() {
        return getStudyMinutes() > 0;
    }

    /**
     * 예상 소요 시간을 "1시간 30분" 처럼 읽히게. 적지 않았으면 null.
     *
     * <p>시작·종료 시각이 <b>둘 다</b> 있으면 null 을 준다 - 그때는 그 구간이 계획 시간이라
     * ({@link #getStudyMinutes}) 예상값을 함께 적으면 화면에 서로 다른 두 숫자가 놓인다.
     * 보이는 값과 계산에 쓰는 값은 같아야 한다.</p>
     */
    public String getReadableEstimate() {
        if (estimatedMinutes == null || (startTime != null && endTime != null)) {
            return null;
        }
        return ReadableDuration.of(estimatedMinutes);
    }

    public void update(String title, String content, PlanCategory category,
                       LocalDate planDate, LocalTime startTime, LocalTime endTime) {
        update(title, content, category, planDate, startTime, endTime, this.estimatedMinutes);
    }

    public void update(String title, String content, PlanCategory category,
                       LocalDate planDate, LocalTime startTime, LocalTime endTime,
                       Integer estimatedMinutes) {
        validateTimeRange(startTime, endTime);
        validateEstimate(estimatedMinutes);
        this.estimatedMinutes = estimatedMinutes;
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

    /**
     * 다른 날짜로 <b>복제</b>한다. 이월(rollover)이 쓴다.
     *
     * <p>― 왜 옮기지 않고 복제하는가<br>
     * 이월이 {@link #moveTo} 로 날짜를 옮기던 때, <b>어제의 기록이 바뀌었다.</b>
     * 어제 계획이 3개였고 1개를 끝냈다면 완료율은 33%다. 남은 2개를 오늘로 옮기면
     * 어제에는 완료한 1개만 남아 <b>100%가 된다</b> — 통계에 없던 완벽한 하루가 생긴다.
     *
     * <p>이 판단은 이미 위에 절반만 적혀 있었다: 완료한 일정은 "지난 기록이 바뀐다" 는 이유로
     * 옮기지 못하게 막아 두었다. 그런데 완료율을 흔드는 것은 분자(완료 수)만이 아니라
     * 분모(전체 수)도 마찬가지다. 기록은 일어난 일을 적는 것이고, <b>못 한 것도 일어난 일이다.</b>
     *
     * <p>복제본은 새 하루의 새 계획이므로 반복 묶음·공유·알림 이력을 물려받지 않는다 -
     * 물려받으면 오늘 것이 어제 것의 그림자가 된다.
     *
     * <p>원본에 표시를 남기지 않는 순수한 복제는 {@link #copyAt} 이다.
     */
    public Plan rolloverTo(LocalDate date) {
        this.rolledOver = true;   // 두 번 눌러도 두 개가 생기지 않게
        return copyAt(date);
    }

    /**
     * 같은 계획을 다른 날짜로 복제한다. 원본은 아무것도 달라지지 않는다.
     *
     * <p>이월({@link #rolloverTo})과 나뉘어 있는 이유는 <b>원본에 남기는 표시가 다르기 때문</b>이다.
     * 이월은 "이 계획을 다음 날로 넘겼다" 는 그날의 사건이라 원본에 기록이 남지만,
     * 지난주 계획을 이번 주로 가져오는 것은 지난주에 아무 일도 일으키지 않는다.
     * 한 메서드가 둘 다 하면, 주간 복사 한 번에 지난주 계획이 전부 '이월함' 으로 표시된다.</p>
     *
     * <p>복제본은 새 날의 새 계획이므로 반복 묶음·공유·알림 이력을 물려받지 않는다.</p>
     */
    public Plan copyAt(LocalDate date) {
        Plan copy = new Plan(title, content, author, category, date, startTime, endTime,
                estimatedMinutes);
        copy.shareScope = ShareScope.PRIVATE;
        return copy;
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

    /**
     * 예상 소요 시간을 검사한다.
     *
     * <p>위쪽 한계를 <b>하루</b>로 둔 것은 통계를 지키기 위해서다. 이 값은 그대로
     * '계획 시간' 에 합산되므로, 단위를 착각해 분 대신 초를 적거나(7200) 0 을 하나 더 누르면
     * 그 하루의 실행률이 0% 근처로 내려앉고 주간 추이까지 함께 망가진다.
     * 하루를 넘는 계획은 하나의 일정이 아니라 여러 날에 걸친 일이므로 나눠 적는 것이 맞다.</p>
     */
    private void validateEstimate(Integer estimatedMinutes) {
        if (estimatedMinutes == null) {
            return;
        }
        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException("예상 소요 시간은 1분 이상이어야 합니다.");
        }
        if (estimatedMinutes > MAX_ESTIMATED_MINUTES) {
            throw new IllegalArgumentException("예상 소요 시간은 24시간을 넘을 수 없습니다.");
        }
    }
}
