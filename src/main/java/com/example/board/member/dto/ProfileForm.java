package com.example.board.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 마이페이지의 프로필 수정 (닉네임·이메일·하루 목표 시간) */
@Getter
@Setter
public class ProfileForm {

    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해 주세요.")
    private String nickname;

    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
    private String email;

    @Min(value = 0, message = "목표 시간은 0분 이상이어야 합니다.")
    @Max(value = 1440, message = "목표 시간은 하루(1440분)를 넘을 수 없습니다.")
    private int dailyGoalMinutes;
}
