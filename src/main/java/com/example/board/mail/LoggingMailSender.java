package com.example.board.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 메일을 로그로 남긴다.
 *
 * <p>SMTP 계정 없이도 비밀번호 재설정 흐름을 끝까지 확인할 수 있게 한다.
 * 콘솔에 찍힌 링크를 그대로 브라우저에 붙여 넣으면 된다.</p>
 */
@Slf4j
@Component
public class LoggingMailSender {

    public void send(String to, String subject, String body) {
        log.info("""

                ┌─ 메일 발송 (개발 모드 - 실제로 보내지 않음) ──────────────
                │ 받는 사람 : {}
                │ 제목      : {}
                ├──────────────────────────────────────────────────────
                {}
                └──────────────────────────────────────────────────────
                """, to, subject, body);
    }
}
