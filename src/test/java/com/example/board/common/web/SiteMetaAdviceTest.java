package com.example.board.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiteMetaAdviceTest {

    @Test
    @DisplayName("끝의 '/' 를 떼어 둔다 - 붙인 채로 두면 공유 이미지 주소가 '//icons/...' 가 된다")
    void trimsTrailingSlash() {
        assertThat(new SiteMetaAdvice("https://study.example.com/").baseUrl())
                .isEqualTo("https://study.example.com");
        assertThat(new SiteMetaAdvice("https://study.example.com").baseUrl())
                .isEqualTo("https://study.example.com");
    }
}
