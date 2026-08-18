package com.example.board.retro.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 회고의 주기.
 *
 * <p>하루치와 한 주치는 "얼마나 길게 쓰는가" 와 "어느 날짜에 매다는가" 가 다르다.
 * 그 두 규칙을 여기에 두어, 서비스가 타입별로 if 를 늘어놓지 않게 한다.</p>
 *
 * <p>주간 회고를 수요일에 쓰든 일요일에 쓰든 같은 주의 회고여야 하므로,
 * 저장 전에 반드시 {@link #anchorDate} 로 날짜를 그 주 월요일에 맞춘다.
 * 이 정규화가 없으면 한 주에 회고가 일곱 개까지 생긴다.</p>
 */
public enum RetroType {

    /** 하루 한 줄 - 길게 쓰라고 하면 아무도 안 쓴다 */
    DAILY("하루 회고", 200),

    WEEKLY("주간 회고", 2000);

    private final String label;
    private final int maxLength;

    RetroType(String label, int maxLength) {
        this.label = label;
        this.maxLength = maxLength;
    }

    public String getLabel() {
        return label;
    }

    public int getMaxLength() {
        return maxLength;
    }

    /** 이 회고가 매달릴 날짜. 주간은 어느 요일에 쓰든 그 주 월요일로 모은다 */
    public LocalDate anchorDate(LocalDate date) {
        return (this == WEEKLY) ? date.with(DayOfWeek.MONDAY) : date;
    }

    /** 길이 규칙 - 주기마다 다르므로 여기서 판단한다 */
    public boolean fits(String content) {
        return content != null && !content.isBlank() && content.strip().length() <= maxLength;
    }
}
