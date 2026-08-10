package com.example.board.common.web;

import org.springframework.data.domain.Page;

/**
 * 페이지 번호 블록(1~5, 6~10 …) 계산 결과.
 *
 * <p>현재 페이지와 전체 페이지 수만으로 결정되는 순수 값 객체라 DB 없이 검증할 수 있고,
 * 블록 크기를 바꾸는 변경이 이 클래스 안에만 머문다.
 * 목록 화면이 늘어나도 컨트롤러마다 계산식을 복사할 필요가 없다.</p>
 */
public record PageBlock(int start, int end, int current, int totalPages) {

    /** 한 번에 보여줄 페이지 번호 개수 */
    private static final int BLOCK_SIZE = 5;

    public static PageBlock of(Page<?> page) {
        return of(page.getNumber(), page.getTotalPages());
    }

    public static PageBlock of(int current, int totalPages) {
        int start = (current / BLOCK_SIZE) * BLOCK_SIZE;
        int end = Math.min(start + BLOCK_SIZE - 1, lastPageOf(totalPages));
        return new PageBlock(start, end, current, totalPages);
    }

    /** 마지막 페이지 번호(0-based). 결과가 없어도 음수가 되지 않는다 */
    public int lastPage() {
        return lastPageOf(totalPages);
    }

    /** 앞쪽에 건너뛴 블록이 있는가 (&laquo; 노출 여부) */
    public boolean hasPreviousBlock() {
        return start > 0;
    }

    /** 뒤쪽에 남은 블록이 있는가 (&raquo; 노출 여부) */
    public boolean hasNextBlock() {
        return end < lastPage();
    }

    private static int lastPageOf(int totalPages) {
        return Math.max(totalPages - 1, 0);
    }
}
