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
import java.time.LocalTime;

@Getter
@Setter
public class PlanForm {

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 100, message = "제목은 100자 이하로 입력하세요.")
    private String title;

    @Size(max = 1000, message = "메모는 1000자 이하로 입력하세요.")
    private String content;

    @NotNull(message = "분류를 선택하세요.")
    private PlanCategory category = PlanCategory.ETC;

    @NotNull(message = "날짜를 선택하세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    /**
     * 예상 소요 시간(분). 비워 두면 null - 0 이 아니다.
     *
     * <p>0 으로 받으면 "0분 걸린다" 와 "안 적었다" 를 구분할 수 없고, 화면은 그 둘에 다르게
     * 반응해야 한다. 빈 문자열이 null 로 들어오도록 {@code Integer} 로 두었다.</p>
     */
    @Min(value = 1, message = "예상 소요 시간은 1분 이상으로 입력하세요.")
    @Max(value = Plan.MAX_ESTIMATED_MINUTES, message = "예상 소요 시간은 24시간을 넘을 수 없습니다.")
    private Integer estimatedMinutes;

    /** 반복 설정 - 새 일정 등록에서만 사용하고 수정 시에는 무시한다 */
    @NotNull(message = "반복 여부를 선택하세요.")
    private RepeatType repeatType = RepeatType.NONE;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate repeatUntil;
}
