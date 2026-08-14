package com.example.board.plan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("공유 범위")
class ShareScopeTest {

    @Test
    @DisplayName("비공개 플랜은 작성자 본인만 볼 수 있다")
    void privateVisibleOnlyToAuthor() {
        assertThat(ShareScope.PRIVATE.visibleTo(true, false)).isTrue();
        assertThat(ShareScope.PRIVATE.visibleTo(false, true)).isFalse();
        assertThat(ShareScope.PRIVATE.visibleTo(false, false)).isFalse();
    }

    @Test
    @DisplayName("그룹 공개 플랜은 작성자와 같은 그룹인 사람까지만 볼 수 있다")
    void groupVisibleToFellows() {
        assertThat(ShareScope.GROUP.visibleTo(true, false)).isTrue();
        assertThat(ShareScope.GROUP.visibleTo(false, true)).isTrue();
        assertThat(ShareScope.GROUP.visibleTo(false, false)).isFalse();
    }

    @Test
    @DisplayName("전체 공개 플랜은 누구나 볼 수 있다")
    void publicVisibleToEveryone() {
        assertThat(ShareScope.PUBLIC.visibleTo(false, false)).isTrue();
    }

    @Test
    @DisplayName("비공개만 공유되지 않은 상태다 - 공유 목록·댓글 노출의 전제 조건")
    void onlyPrivateIsNotShared() {
        assertThat(ShareScope.PRIVATE.isShared()).isFalse();
        assertThat(ShareScope.GROUP.isShared()).isTrue();
        assertThat(ShareScope.PUBLIC.isShared()).isTrue();
    }
}
