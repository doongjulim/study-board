package com.example.board.calendar.domain;

import com.example.board.plan.domain.Plan;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 계획을 CSV(RFC 4180)로 옮긴다.
 *
 * <p>맨 앞에 BOM 을 붙이는 이유는 엑셀 때문이다. 윈도우 엑셀은 BOM 이 없는 UTF-8 파일을
 * 시스템 인코딩으로 읽어 한글을 전부 깨뜨린다. 내보내기의 목적이 "엑셀에서 열어 보기" 라면
 * BOM 이 없는 파일은 사실상 못 쓰는 파일이다.</p>
 */
public final class PlanCsv {

    /** 엑셀이 UTF-8 로 알아보게 하는 표식 */
    public static final String BOM = "﻿";

    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> HEADERS =
            List.of("날짜", "시작", "종료", "분류", "제목", "메모", "완료", "공유 범위");

    private PlanCsv() {
    }

    public static String render(List<Plan> plans) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(String.join(",", HEADERS)).append(CRLF);
        plans.forEach(plan -> csv.append(row(plan)).append(CRLF));
        return csv.toString();
    }

    private static String row(Plan plan) {
        return String.join(",",
                quote(plan.getPlanDate().format(DATE)),
                quote(plan.getStartTime() != null ? plan.getStartTime().format(TIME) : ""),
                quote(plan.getEndTime() != null ? plan.getEndTime().format(TIME) : ""),
                quote(plan.getCategory().getLabel()),
                quote(plan.getTitle()),
                quote(plan.getContent() != null ? plan.getContent() : ""),
                quote(plan.isCompleted() ? "완료" : "미완료"),
                quote(plan.getShareScope().getLabel()));
    }

    /**
     * 모든 칸을 큰따옴표로 감싼다.
     *
     * <p>쉼표·줄바꿈이 든 칸만 골라 감싸는 것이 규격이지만, 제목과 메모는 사용자가 자유롭게 쓰는 값이라
     * 조건을 두면 언젠가 빠뜨린다. 값 안의 큰따옴표는 두 번 겹쳐 이스케이프한다.</p>
     */
    static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
