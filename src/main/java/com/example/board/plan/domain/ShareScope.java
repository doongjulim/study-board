package com.example.board.plan.domain;

/**
 * 플랜 공유 범위.
 *
 * <p>예전에는 공유가 켜짐/꺼짐 두 단계였고 켜면 전체 공개였다.
 * 스터디 그룹이 생기면서 "우리끼리만 보자"는 중간 단계가 필요해졌다.
 * 누가 볼 수 있는지의 판단을 이 enum 에 두어, 화면·쿼리·알림이
 * 각자 다른 규칙을 갖게 되는 것을 막는다.</p>
 */
public enum ShareScope {

    PRIVATE("비공개"),
    GROUP("그룹 공개"),
    PUBLIC("전체 공개");

    private final String label;

    ShareScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 어떤 형태로든 공유된 상태인지 (댓글·공유 목록 노출의 전제 조건) */
    public boolean isShared() {
        return this != PRIVATE;
    }

    /**
     * 이 범위의 플랜을 볼 수 있는 사람인지 판정한다.
     *
     * @param author 보는 사람이 작성자 본인인가
     * @param fellow 보는 사람이 작성자와 같은 그룹에 속해 있는가
     */
    public boolean visibleTo(boolean author, boolean fellow) {
        return switch (this) {
            case PRIVATE -> author;
            case GROUP -> author || fellow;
            case PUBLIC -> true;
        };
    }
}
