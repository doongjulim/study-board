package com.example.board.member.exception;

import lombok.Getter;

/** 아이디/닉네임 중복 시 발생. field 로 어느 입력 항목의 오류인지 구분한다. */
@Getter
public class DuplicateMemberException extends RuntimeException {

    private final String field;

    public DuplicateMemberException(String field, String message) {
        super(message);
        this.field = field;
    }
}
