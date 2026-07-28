package com.example.board.post.dto;

/** 게시글 검색 조건 */
public enum SearchType {
    TITLE("제목"),
    TITLE_CONTENT("제목+내용"),
    WRITER("작성자");

    private final String label;

    SearchType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
