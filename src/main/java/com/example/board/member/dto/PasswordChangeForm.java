package com.example.board.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 비밀번호 변경 - 현재 비밀번호를 함께 확인해 세션 탈취 상황을 막는다 */
@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
    private String newPasswordConfirm;

    public boolean isConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
