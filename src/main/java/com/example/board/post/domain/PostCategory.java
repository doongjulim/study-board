package com.example.board.post.domain;

/**
 * 게시글 분류.
 *
 * <p>지금까지 게시판은 인증글·질문·후기가 한 줄로 섞여 있어, 무엇을 찾으려 해도 목록을 처음부터 훑어야 했다.
 * 취준 커뮤니티로 쓰려면 "지금 나에게 필요한 글" 만 볼 수 있어야 한다.</p>
 *
 * <p>{@link #FREE} 를 기본값으로 둔 이유는 기존 글들이 들어갈 자리가 필요해서다.
 * 새 분류를 뒤늦게 도입할 때 기존 데이터를 어디에 둘지 정하지 않으면 마이그레이션이 막힌다.</p>
 */
public enum PostCategory {

    FREE("자유"),
    RECRUIT("공고"),
    REVIEW("후기"),
    QUESTION("질문");

    private final String label;

    PostCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
