package com.example.board.plan.dto;

import com.example.board.plan.domain.PlanCategory;
import com.example.board.plan.domain.RepeatType;
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
    @Size(max = 100, message = "제목은 100자 이하로 입력하세요.")
    private String title;

    @NotNull(message = "날짜를 선택하세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    private PlanCategory category = PlanCategory.ETC;

    /** 기존 등록 경로(PlanService.create)를 그대로 쓰기 위한 변환 */
    public PlanForm toPlanForm() {
        PlanForm form = new PlanForm();
        form.setTitle(title);
        form.setPlanDate(planDate);
        form.setCategory(category != null ? category : PlanCategory.ETC);
        form.setRepeatType(RepeatType.NONE);
        return form;
    }
}
