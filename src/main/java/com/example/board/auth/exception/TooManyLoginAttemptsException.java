package com.example.board.auth.exception;

/**
 * 로그인 시도가 너무 잦아 잠시 막힌 상태.
 *
 * <p>{@link LoginFailedException} 과 구분하는 이유는 사용자에게 할 말이 다르기 때문이다 -
 * 하나는 "다시 확인하세요", 다른 하나는 "잠시 뒤에 다시 시도하세요" 다.</p>
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("로그인 시도가 너무 많습니다. %d분 뒤에 다시 시도해 주세요."
                .formatted(Math.max(1, (retryAfterSeconds + 59) / 60)));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
