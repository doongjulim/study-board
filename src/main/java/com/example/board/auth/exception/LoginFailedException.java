package com.example.board.auth.exception;

/** 아이디 없음/비밀번호 불일치 - 보안상 어느 쪽이 틀렸는지 구분하지 않는다. */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException() {
        super("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
