package com.example.board.plan.dto;

import com.example.board.plan.domain.Plan;
import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.RepeatType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 한 줄 입력으로 일정을 추가할 때 쓰는 폼.
 *
 * <p>제목과 날짜만 있으면 등록된다. 시간·메모·반복은 나중에 수정 화면에서 채우면 되고,
 * 떠오른 할 일을 적는 데 필드 일곱 개를 채우게 하면 결국 아무것도 적지 않게 된다.</p>
 */
@Getter
@Setter
public class QuickPlanForm {

    @NotBlank(message = "할 일을 입력하세요.")
    @Size(max = Plan.MAX_TITLE_LENGTH, message = "제목은 100자 이하로 입력하세요.")
    private String title;

    @NotNull(message = "날짜를 선택하세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    private PlanCategory category = PlanCategory.ETC;

    /**
     * 예상 소요 시간(분). 칩(30분·1시간·2시간)으로 넣는다.
     *
     * <p>한 줄 추가에 필드를 늘리는 것은 이 폼의 취지에 어긋나지만, 이 값만은 예외로 두었다.
     * 시각과 달리 <b>클릭 한 번</b>이면 되고, 이것이 없으면 통계의 '계획 대비 실행률' 이
     * 주 입력 경로를 쓰는 사람에게는 영영 보이지 않기 때문이다.</p>
     */
    @Min(value = 1, message = "예상 소요 시간은 1분 이상으로 입력하세요.")
    @Max(value = Plan.MAX_ESTIMATED_MINUTES, message = "예상 소요 시간은 24시간을 넘을 수 없습니다.")
    private Integer estimatedMinutes;

    /** 기존 등록 경로(PlanService.create)를 그대로 쓰기 위한 변환 */
    public PlanForm toPlanForm() {
        PlanForm form = new PlanForm();
        form.setTitle(title);
        form.setPlanDate(planDate);
        form.setCategory(category != null ? category : PlanCategory.ETC);
        form.setEstimatedMinutes(estimatedMinutes);
        form.setRepeatType(RepeatType.NONE);
        return form;
    }
}
