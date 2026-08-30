package com.example.board.post.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회수를 새로고침마다 올리지 않기 위한 값 객체.
 * 쿠키 문자열 하나만 다루므로 DB 없이 규칙을 전부 확인할 수 있다.
 */
class ViewedPostsTest {

    @Nested
    @DisplayName("읽기")
    class Read {

        @Test
        @DisplayName("쿠키가 없으면 아무 글도 본 적이 없는 상태다")
        void noCookie() {
            assertThat(ViewedPosts.from(null).ids()).isEmpty();
        }

        @Test
        @DisplayName("다른 쿠키만 있으면 빈 목록이다")
        void otherCookieOnly() {
            Cookie[] cookies = {new Cookie("ACCESS_TOKEN", "abc")};

            assertThat(ViewedPosts.from(cookies).ids()).isEmpty();
        }

        @Test
        @DisplayName("쉼표로 이어진 값을 읽는다")
        void parsesIds() {
            ViewedPosts viewed = ViewedPosts.from(cookie("3,1,2"));

            assertThat(viewed.contains(1L)).isTrue();
            assertThat(viewed.contains(9L)).isFalse();
        }

        @Test
        @DisplayName("사람이 고칠 수 있는 값이므로, 숫자가 아닌 조각은 조용히 버린다")
        void ignoresGarbage() {
            // 여기서 예외를 던지면 쿠키를 손댄 사람이 글을 못 여는 정도가 아니라 500 이 된다
            ViewedPosts viewed = ViewedPosts.from(cookie("1,abc,,2, 3 "));

            assertThat(viewed.ids()).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("같은 id 가 여러 번 있어도 한 번만 센다")
        void deduplicates() {
            assertThat(ViewedPosts.from(cookie("1,1,2")).ids()).containsExactly(1L, 2L);
        }
    }

    @Nested
    @DisplayName("기록")
    class Add {

        @Test
        @DisplayName("본 글을 앞에 더한다")
        void addsNewest() {
            ViewedPosts viewed = ViewedPosts.from(cookie("2,3")).plus(1L);

            assertThat(viewed.ids()).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("이미 본 글은 목록을 바꾸지 않는다")
        void alreadyViewed() {
            ViewedPosts before = ViewedPosts.from(cookie("2,3"));

            assertThat(before.plus(2L).ids()).containsExactly(2L, 3L);
        }

        @Test
        @DisplayName("상한을 넘으면 오래된 것부터 버린다 - 쿠키가 무한히 길어지면 브라우저가 대신 잘라 낸다")
        void keepsOnlyRecent() {
            String many = LongStream.rangeClosed(1, 60)
                    .mapToObj(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElseThrow();

            ViewedPosts viewed = ViewedPosts.from(cookie(many)).plus(100L);

            assertThat(viewed.ids()).hasSize(50);
            assertThat(viewed.ids().get(0)).isEqualTo(100L);
            assertThat(viewed.contains(60L)).isFalse();
        }
    }

    @Nested
    @DisplayName("쿠키로 만들기")
    class ToCookie {

        @Test
        @DisplayName("쉼표로 이어 붙이고, 하루가 지나면 만료된다")
        void writesCookie() {
            Cookie cookie = ViewedPosts.from(cookie("2,3")).plus(1L).toCookie();

            assertThat(cookie.getName()).isEqualTo(ViewedPosts.COOKIE_NAME);
            assertThat(cookie.getValue()).isEqualTo("1,2,3");
            assertThat(cookie.getMaxAge()).isEqualTo(60 * 60 * 24);
        }

        @Test
        @DisplayName("스크립트가 읽을 이유가 없으므로 HttpOnly 다")
        void httpOnly() {
            assertThat(ViewedPosts.from(null).plus(1L).toCookie().isHttpOnly()).isTrue();
        }
    }

    private static Cookie[] cookie(String value) {
        return new Cookie[]{new Cookie(ViewedPosts.COOKIE_NAME, value)};
    }
}
