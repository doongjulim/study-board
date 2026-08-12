package com.example.board.mail;

/**
 * 메일 발송 창구.
 *
 * <p>{@code FileStore} 와 같은 자리다 — 이 인터페이스만 구현하면
 * 콘솔 출력에서 SMTP·SES 같은 실제 발송으로 옮겨갈 수 있고,
 * 호출하는 쪽(비밀번호 재설정 등)은 손대지 않는다.</p>
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
