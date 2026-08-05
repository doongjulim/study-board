package com.example.board.dday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class DdayForm {

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 50, message = "제목은 50자 이하로 입력하세요.")
    private String title;

    @NotNull(message = "목표 날짜를 선택하세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetDate;
}
