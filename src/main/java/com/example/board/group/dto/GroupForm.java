package com.example.board.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupForm {

    @NotBlank(message = "그룹 이름을 입력하세요.")
    @Size(max = 30, message = "그룹 이름은 30자 이하로 입력하세요.")
    private String name;

    @Size(max = 200, message = "소개는 200자 이하로 입력하세요.")
    private String description;
}
