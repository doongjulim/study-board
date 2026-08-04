package com.example.board.auth;

/** SecurityContext 에 올라가는 인증 주체. 토큰 클레임만으로 구성되어 요청마다 DB 를 조회하지 않는다. */
public record MemberPrincipal(Long id, String loginId, String nickname) {
}
