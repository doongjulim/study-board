package com.example.board.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 화면의 입력칸과 버튼에 <b>이름</b>이 있는가.
 *
 * <p>― 왜 필요한가<br>
 * 이 프로젝트는 화면 규칙을 문서와 테스트로 꽤 지켜 왔지만, 그 규칙들은 "무엇을 그리는가" 에
 * 관한 것이다. <b>라벨 없는 입력칸</b>은 화면에는 아무 표도 나지 않는다 - 눈으로 보는 사람에게는
 * 멀쩡하고, 스크린리더로 듣는 사람에게만 "편집, 비어 있음" 이라고 들린다.
 * 실제로 게시판 검색줄의 칸 셋이 그 상태로 오래 있었다.
 *
 * <p>{@code placeholder} 는 라벨이 아니다. 값을 적는 순간 사라지고, 무엇을 묻는 칸이었는지
 * 다시 확인할 방법이 없어진다. 화면에 라벨을 두고 싶지 않으면 {@code .sr-only} 로 감춘다.
 *
 * <p>CI 의 Lighthouse 가 같은 것을 보지만, 여기서도 보는 이유는 <b>고치는 자리가 다르기 때문</b>이다 -
 * Lighthouse 는 푸시한 뒤에 알려 주고, 이 테스트는 고치는 도중에 알려 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("입력칸 이름")
class AccessibleFormsTest {

    /** 로그인 없이 열리는 화면들 - 헤더·푸터는 어차피 모든 화면이 공유한다 */
    private static final List<String> PUBLIC_PAGES = List.of("/", "/login", "/signup", "/posts");

    private static final Pattern LABEL_FOR = Pattern.compile("<label[^>]*\\sfor=\"([^\"]+)\"");
    private static final Pattern CONTROL = Pattern.compile("<(input|select|textarea)\\b[^>]*>");
    private static final Pattern ID = Pattern.compile("\\sid=\"([^\"]+)\"");
    private static final Pattern BUTTON = Pattern.compile("<button\\b([^>]*)>(.*?)</button>", Pattern.DOTALL);

    /** 이름이 필요 없는 것들 - 사용자가 보지도 만지지도 않는다 */
    private static final List<String> NAMELESS_TYPES = List.of("hidden", "submit", "reset", "button");

    @Autowired
    private MockMvc mockMvc;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("입력칸에는 라벨이 붙어 있다 - placeholder 는 라벨이 아니다")
    void everyControlHasALabel() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String page : PUBLIC_PAGES) {
            String html = render(page);
            Set<String> labelled = new HashSet<>();
            Matcher labels = LABEL_FOR.matcher(html);
            while (labels.find()) {
                labelled.add(labels.group(1));
            }

            Matcher controls = CONTROL.matcher(html);
            while (controls.find()) {
                String tag = controls.group();
                if (isNameless(tag) || tag.contains("aria-label")) {
                    continue;
                }
                Matcher id = ID.matcher(tag);
                if (!id.find() || !labelled.contains(id.group(1))) {
                    offenders.add(page + " → " + tag.trim());
                }
            }
        }

        assertThat(offenders)
                .as("라벨 없는 입력칸이 있다. 화면에 두기 싫으면 <label class=\"sr-only\"> 로 감춘다")
                .isEmpty();
    }

    @Test
    @DisplayName("버튼에는 읽을 이름이 있다 - 아이콘만 있는 버튼은 aria-label 로 말한다")
    void everyButtonHasAName() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String page : PUBLIC_PAGES) {
            Matcher buttons = BUTTON.matcher(render(page));
            while (buttons.find()) {
                String attributes = buttons.group(1);
                String text = buttons.group(2).replaceAll("<[^>]*>", "").trim();
                if (text.isEmpty() && !attributes.contains("aria-label")) {
                    offenders.add(page + " → <button" + attributes + ">");
                }
            }
        }

        assertThat(offenders).as("이름 없는 버튼이 있다").isEmpty();
    }

    @Test
    @DisplayName("이미지에는 대체 텍스트가 있다")
    void everyImageHasAlt() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String page : PUBLIC_PAGES) {
            Matcher images = Pattern.compile("<img\\b[^>]*>").matcher(render(page));
            while (images.find()) {
                if (!images.group().contains("alt=")) {
                    offenders.add(page + " → " + images.group());
                }
            }
        }

        assertThat(offenders).as("alt 없는 이미지가 있다 (장식이면 alt=\"\" 로 비워 둔다)").isEmpty();
    }

    private boolean isNameless(String tag) {
        return NAMELESS_TYPES.stream().anyMatch(type -> tag.contains("type=\"" + type + "\""));
    }
}
