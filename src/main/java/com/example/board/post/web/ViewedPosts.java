package com.example.board.post.web;

import jakarta.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 이 브라우저가 최근에 열어 본 글 목록 (쿠키에 담긴다).
 *
 * <p>조회수가 새로고침마다 올라갔다. 글쓴이 본인의 조회는 빼고 있었지만, 비로그인 조회가 섞여 있어
 * "누가 봤는가" 를 서버에 남길 수는 없다 - 그렇게 하면 로그인하지 않은 사람을 식별하겠다는 뜻이 된다.
 * 그래서 "이미 봤다" 는 사실을 <b>그 브라우저에</b> 남긴다. 개인을 특정하지 않고 중복만 걷어낸다.</p>
 *
 * <p>순수 값 객체다 - 쿠키 문자열을 다루는 규칙(길이 제한, 잘못된 값 무시, 최근 것 우선)을
 * 컨트롤러가 아니라 여기에 두어 DB 없이 검증한다.</p>
 */
public final class ViewedPosts {

    public static final String COOKIE_NAME = "viewed_posts";

    /** 하루가 지나면 다시 한 번 센다 - 어제 본 글을 오늘 다시 보는 것은 새 조회로 볼 만하다 */
    private static final int MAX_AGE_SECONDS = 60 * 60 * 24;

    /**
     * 기억하는 글 수의 상한.
     *
     * <p>상한이 없으면 많이 읽는 사람일수록 쿠키가 길어져, 언젠가 브라우저의 쿠키 크기 한도(4KB)에
     * 부딪혀 조용히 잘린다. 그때 무엇이 잘릴지는 브라우저가 정한다. 그럴 바에는 우리가 정한다 -
     * 오래된 것부터 버리고, 최근에 본 글만 남긴다.</p>
     */
    private static final int MAX_SIZE = 50;

    private static final String DELIMITER = ",";

    private final List<Long> ids;

    private ViewedPosts(List<Long> ids) {
        this.ids = ids;
    }

    /** 요청에 실려 온 쿠키에서 읽어 온다. 쿠키가 없거나 값이 망가져 있으면 빈 목록이다 */
    public static ViewedPosts from(Cookie[] cookies) {
        if (cookies == null) {
            return new ViewedPosts(List.of());
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .findFirst()
                .map(cookie -> parse(cookie.getValue()))
                .orElseGet(() -> new ViewedPosts(List.of()));
    }

    private static ViewedPosts parse(String value) {
        if (value == null || value.isBlank()) {
            return new ViewedPosts(List.of());
        }
        List<Long> parsed = new ArrayList<>();
        for (String token : value.split(DELIMITER)) {
            // 쿠키는 사용자가 고칠 수 있는 값이다. 숫자가 아니면 조용히 버린다 -
            // 여기서 예외를 던지면 쿠키를 손댄 사람이 글을 못 여는 것으로 끝나지 않고 500 이 된다
            try {
                Long id = Long.valueOf(token.trim());
                if (!parsed.contains(id)) {
                    parsed.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 다음 값으로 넘어간다
            }
        }
        return new ViewedPosts(List.copyOf(parsed));
    }

    public boolean contains(Long postId) {
        return ids.contains(postId);
    }

    /** 이 글을 본 것으로 기록한 새 목록 (값 객체라 원본은 그대로 둔다) */
    public ViewedPosts plus(Long postId) {
        if (contains(postId)) {
            return this;
        }
        List<Long> next = new ArrayList<>();
        next.add(postId); // 최근 것이 앞 - 상한을 넘으면 뒤(오래된 것)부터 잘린다
        next.addAll(ids);
        return new ViewedPosts(List.copyOf(next.subList(0, Math.min(next.size(), MAX_SIZE))));
    }

    public List<Long> ids() {
        return ids;
    }

    /**
     * 브라우저에 돌려줄 쿠키.
     *
     * <p>HttpOnly 다 - 화면의 스크립트가 읽을 이유가 없고, 읽히지 않아야 XSS 로 새어 나가지 않는다.
     * 인증 쿠키와 같은 SameSite 정책은 여기서 필요 없지만, 경로는 사이트 전체로 둔다(글은 어디서든 열린다).</p>
     */
    public Cookie toCookie() {
        String value = ids.stream().map(String::valueOf).reduce((a, b) -> a + DELIMITER + b).orElse("");
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(MAX_AGE_SECONDS);
        return cookie;
    }
}
