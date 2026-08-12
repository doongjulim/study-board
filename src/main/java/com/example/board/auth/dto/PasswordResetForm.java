package com.example.board.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 메일 링크로 들어와 새 비밀번호를 정하는 폼 */
@Getter
@Setter
public class PasswordResetForm {

    @NotBlank
    private String token;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
    private String newPasswordConfirm;

    public boolean isConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
