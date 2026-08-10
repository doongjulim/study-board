package com.example.board.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageBlock 은 페이지 번호 블록을 계산한다")
class PageBlockTest {

    @Nested
    @DisplayName("블록 범위")
    class BlockRange {

        @Test
        @DisplayName("첫 페이지는 0~4 블록에 속한다")
        void firstBlock() {
            PageBlock block = PageBlock.of(0, 12);

            assertThat(block.start()).isZero();
            assertThat(block.end()).isEqualTo(4);
        }

        @Test
        @DisplayName("5페이지째부터 다음 블록(5~9)으로 넘어간다")
        void secondBlock() {
            PageBlock block = PageBlock.of(5, 12);

            assertThat(block.start()).isEqualTo(5);
            assertThat(block.end()).isEqualTo(9);
        }

        @Test
        @DisplayName("마지막 블록은 전체 페이지 수를 넘지 않는다")
        void lastBlockIsClamped() {
            PageBlock block = PageBlock.of(10, 12);

            assertThat(block.start()).isEqualTo(10);
            assertThat(block.end()).isEqualTo(11); // 총 12페이지 -> 마지막 번호는 11
        }

        @Test
        @DisplayName("결과가 하나도 없으면 0~0 이 되고 음수가 나오지 않는다")
        void emptyResult() {
            PageBlock block = PageBlock.of(0, 0);

            assertThat(block.start()).isZero();
            assertThat(block.end()).isZero();
            assertThat(block.lastPage()).isZero();
        }
    }

    @Nested
    @DisplayName("블록 이동 링크 노출 여부")
    class BlockNavigation {

        @Test
        @DisplayName("첫 블록에서는 이전 블록 링크를 감춘다")
        void hidesPreviousOnFirstBlock() {
            assertThat(PageBlock.of(0, 12).hasPreviousBlock()).isFalse();
        }

        @Test
        @DisplayName("두 번째 블록부터는 이전 블록 링크를 보여준다")
        void showsPreviousFromSecondBlock() {
            assertThat(PageBlock.of(5, 12).hasPreviousBlock()).isTrue();
        }

        @Test
        @DisplayName("뒤에 페이지가 남아 있으면 다음 블록 링크를 보여준다")
        void showsNextWhenMorePagesRemain() {
            assertThat(PageBlock.of(0, 12).hasNextBlock()).isTrue();
        }

        @Test
        @DisplayName("마지막 블록에서는 다음 블록 링크를 감춘다")
        void hidesNextOnLastBlock() {
            assertThat(PageBlock.of(10, 12).hasNextBlock()).isFalse();
        }

        @Test
        @DisplayName("페이지가 블록 하나에 다 들어가면 양쪽 링크가 모두 없다")
        void singleBlock() {
            PageBlock block = PageBlock.of(0, 3);

            assertThat(block.hasPreviousBlock()).isFalse();
            assertThat(block.hasNextBlock()).isFalse();
            assertThat(block.end()).isEqualTo(2);
        }
    }
}
