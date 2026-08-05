package com.example.board.plan.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 반복 일정 규칙. 반복되는 날짜를 직접 만들어 주므로 규칙 변경이 이곳에만 머문다. */
public enum RepeatType {

    NONE("반복 안 함") {
        @Override
        boolean matches(LocalDate date) {
            return true;
        }
    },
    DAILY("매일") {
        @Override
        boolean matches(LocalDate date) {
            return true;
        }
    },
    WEEKDAY("평일(월~금)") {
        @Override
        boolean matches(LocalDate date) {
            return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
        }
    },
    WEEKLY("매주") {
        @Override
        boolean matches(LocalDate date) {
            return true; // 시작일과 같은 요일만 순회하므로 별도 조건이 없다
        }
    };

    /** 한 번에 만들 수 있는 최대 일정 수 - 실수로 수천 건이 생성되는 것을 막는다 */
    public static final int MAX_OCCURRENCES = 180;

    private final String label;

    RepeatType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRepeating() {
        return this != NONE;
    }

    abstract boolean matches(LocalDate date);

    /**
     * 시작일부터 종료일까지 반복되는 날짜를 만든다.
     * NONE 이면 시작일 하루만, 종료일이 없으면 시작일 기준으로 처리한다.
     */
    public List<LocalDate> datesBetween(LocalDate start, LocalDate until) {
        if (this == NONE || until == null || until.isBefore(start)) {
            return List.of(start);
        }

        List<LocalDate> dates = new ArrayList<>();
        int step = (this == WEEKLY) ? 7 : 1;
        for (LocalDate date = start;
             !date.isAfter(until) && dates.size() < MAX_OCCURRENCES;
             date = date.plusDays(step)) {
            if (matches(date)) {
                dates.add(date);
            }
        }
        return dates;
    }

    /** 종료일까지 만들면 상한을 넘는지 - 폼 검증에서 미리 알려주기 위해 사용한다 */
    public boolean exceedsLimit(LocalDate start, LocalDate until) {
        return isRepeating() && until != null && !until.isBefore(start)
                && datesBetween(start, until).size() >= MAX_OCCURRENCES;
    }
}
