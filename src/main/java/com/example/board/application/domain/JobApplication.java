package com.example.board.application.domain;

import com.example.board.member.domain.Member;
import com.example.board.plan.domain.PlanCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 한 회사에 낸 지원.
 *
 * <p>― 왜 이 앱에 있어야 하는가<br>
 * 취준생이 실제로 가장 많이 관리하는 것은 <b>어느 회사에 넣었고, 지금 어느 단계이고, 언제까지인지</b>다.
 * 그런데 이 앱에는 그것을 담을 자리가 없어 다들 엑셀을 따로 썼다.
 * 계획과 기록이 여기 있는데 그 기록을 <b>왜</b> 쌓는지가 저기 있으면, 둘 다 반쪽이 된다.
 *
 * <p>― 무엇이 이어지는가<br>
 * 마감일은 D-Day 와 같은 자리에 뜨고({@link #remainingDays}), 단계는 학습 분류와 이어진다
 * ({@link ApplicationStage}). 코테 단계인 회사가 셋인데 이번 주 코딩테스트 학습이 0시간이면
 * 분류별 통계가 비로소 <b>판단</b>이 된다 - 지금까지는 그냥 숫자였다.
 *
 * <p>― 왜 회사와 직무를 나누는가<br>
 * 같은 회사에 다른 직무로 넣는 일이 흔하다. 한 칸에 "네이버 백엔드" 로 적게 하면
 * 회사별로 모아 보는 것이 영영 불가능해진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "job_application")
public class JobApplication {

    public static final int MAX_COMPANY_LENGTH = 50;
    public static final int MAX_POSITION_LENGTH = 50;
    public static final int MAX_MEMO_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = MAX_COMPANY_LENGTH)
    private String company;

    /**
     * 직무. 공고에 적히지 않은 경우가 있어 비워 둘 수 있다.
     *
     * <p>컬럼 이름이 {@code job_position} 인 것은 {@code position} 이 SQL 표준의 함수 이름이라
     * 벤더에 따라 식별자로 쓰기 까다롭기 때문이다. 코드에서는 그대로 position 으로 읽는다.</p>
     */
    @Column(name = "job_position", length = MAX_POSITION_LENGTH)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationResult result = ApplicationResult.IN_PROGRESS;

    /**
     * 다음 마감일 - 서류 마감, 코테 응시 기한, 면접 날짜.
     *
     * <p>단계마다 바뀌는 값이라 지원 한 건에 하나만 둔다. 단계별로 날짜를 다 들고 있으면
     * 입력이 다섯 칸이 되고, 그러면 아무도 채우지 않는다 - 지금 필요한 것은 '다음 날짜' 하나다.</p>
     */
    private LocalDate deadline;

    @Column(length = MAX_MEMO_LENGTH)
    private String memo;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public JobApplication(Member owner, String company, String position,
                          ApplicationStage stage, LocalDate deadline, String memo) {
        this.owner = owner;
        this.company = requireCompany(company);
        this.position = blankToNull(position);
        this.stage = (stage != null) ? stage : ApplicationStage.DOCUMENT;
        this.deadline = deadline;
        this.memo = blankToNull(memo);
    }

    public void update(String company, String position, ApplicationStage stage,
                       ApplicationResult result, LocalDate deadline, String memo) {
        this.company = requireCompany(company);
        this.position = blankToNull(position);
        this.stage = (stage != null) ? stage : this.stage;
        this.result = (result != null) ? result : this.result;
        this.deadline = deadline;
        this.memo = blankToNull(memo);
    }

    public boolean isOwnedBy(Long memberId) {
        return owner.getId().equals(memberId);
    }

    public boolean isOngoing() {
        return result.isOngoing();
    }

    /** 지금 단계에서 하게 되는 공부. 결과 대기이거나 끝난 지원에는 없다 */
    public PlanCategory studyCategory() {
        return isOngoing() ? stage.getStudyCategory() : null;
    }

    /** 마감까지 남은 일수. 마감일이 없으면 null (D-Day 와 같은 셈법) */
    public Long remainingDays(LocalDate today) {
        return (deadline == null) ? null : ChronoUnit.DAYS.between(today, deadline);
    }

    /**
     * 화면에 적을 마감 표기 (D-7 / D-DAY / D+3). 마감일이 없으면 null.
     *
     * <p>{@code Dday} 와 같은 규칙을 쓴다 - 같은 화면에 나란히 놓이는 값이 서로 다른 모양이면
     * 사용자는 둘을 다른 것으로 읽는다.</p>
     */
    public String deadlineLabel(LocalDate today) {
        Long remaining = remainingDays(today);
        if (remaining == null) {
            return null;
        }
        if (remaining == 0) {
            return "D-DAY";
        }
        return remaining > 0 ? "D-" + remaining : "D+" + Math.abs(remaining);
    }

    /**
     * 곧 다가오는 마감인가 - 화면이 앞에 끌어올릴지 정할 때 쓴다.
     *
     * <p>끝난 지원의 지난 마감일은 알릴 것이 아니다. 지난 마감이라도 진행 중이면 보여 준다 -
     * 넘겼다는 사실 자체가 알아야 할 정보다.</p>
     */
    public boolean isUpcoming(LocalDate today, int withinDays) {
        Long remaining = remainingDays(today);
        return isOngoing() && remaining != null && remaining <= withinDays;
    }

    private static String requireCompany(String company) {
        if (company == null || company.isBlank()) {
            throw new IllegalArgumentException("회사명을 입력하세요.");
        }
        return company.trim();
    }

    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
