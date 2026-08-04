package com.example.board.plan.dto;

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

    @NotNull(message = "날짜를 선택하세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;
}
