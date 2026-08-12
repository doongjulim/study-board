package com.example.board.member.event;

/**
 * 회원이 탈퇴했음을 알리는 이벤트.
 *
 * <p>탈퇴하면 그 사람의 개인 학습 데이터(계획·목표일·학습 기록·알림)는 지워야 한다.
 * 그렇다고 member 모듈이 그 모듈들을 전부 알게 되면, 기능이 늘 때마다
 * 탈퇴 코드를 함께 고쳐야 하는 구조가 된다.</p>
 *
 * <p>대신 이 이벤트만 발행하고, 각 모듈이 자기 데이터를 스스로 정리하게 한다.
 * 새 모듈이 생겨도 리스너만 추가하면 되고 member 모듈은 그대로다.</p>
 */
public record MemberWithdrawnEvent(Long memberId) {
}
