package com.example.board.stats.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 통계가 보는 기간 - 주 또는 달.
 *
 * <p>― 왜 값 객체로 뺐는가<br>
 * 기간을 구하는 계산(주의 시작은 월요일, 달의 끝은 말일, 이전/다음은 한 주 또는 한 달)이
 * 컨트롤러 안에서 {@code monthly ? ... : ...} 삼항 연산자 여섯 줄로 되어 있었다.
 * 여기에 '지난 기간 대비' 비교가 들어오면 같은 분기가 한 벌 더 생기고, 그때부터
 * <b>한쪽만 고치는 일</b>이 시작된다 - 이 프로젝트가 일정 한 줄과 날짜 포맷에서 이미 겪은 것이다.
 *
 * <p>기간의 종류가 늘어날 자리이기도 하다(하루·분기·올해). 그때 고칠 곳이 여기 하나가 된다.
 */
public record StatsPeriod(LocalDate from, LocalDate to, boolean monthly) {

    private static final String MONTH = "month";

    /** 화면이 넘겨 주는 문자열로 기간을 만든다. 아는 값이 아니면 주간이다 - 주소를 손으로 고쳐도 화면이 뜬다 */
    public static StatsPeriod of(String period, LocalDate target) {
        return MONTH.equals(period) ? month(target) : week(target);
    }

    public static StatsPeriod week(LocalDate anyDay) {
        LocalDate monday = anyDay.with(DayOfWeek.MONDAY);
        return new StatsPeriod(monday, monday.plusDays(6), false);
    }

    public static StatsPeriod month(LocalDate anyDay) {
        LocalDate first = anyDay.withDayOfMonth(1);
        return new StatsPeriod(first, first.withDayOfMonth(first.lengthOfMonth()), true);
    }

    public StatsPeriod previous() {
        return monthly ? month(from.minusMonths(1)) : week(from.minusWeeks(1));
    }

    public StatsPeriod next() {
        return monthly ? month(from.plusMonths(1)) : week(from.plusWeeks(1));
    }

    /** 화면이 다시 넘겨 줄 값 (`?period=`) */
    public String key() {
        return monthly ? MONTH : "week";
    }

    public String label() {
        return monthly ? "이번 달" : "이번 주";
    }

    /** 비교 문구에 쓰는 이름 - "지난주 대비 +2시간" */
    public String previousLabel() {
        return monthly ? "지난달" : "지난주";
    }
}
