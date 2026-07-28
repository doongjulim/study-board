package com.example.board.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 뷰에서 ${currentMember} 로 로그인 회원(비로그인 시 null)에 접근할 수 있게 한다 */
@ControllerAdvice
public class CurrentMemberAdvice {

    @ModelAttribute("currentMember")
    public MemberPrincipal currentMember(@AuthenticationPrincipal MemberPrincipal principal) {
        return principal;
    }
}
