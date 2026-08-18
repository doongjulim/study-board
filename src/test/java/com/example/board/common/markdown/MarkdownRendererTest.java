package com.example.board.common.markdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("마크다운 렌더링")
class MarkdownRendererTest {

    @Test
    @DisplayName("기본 문법을 HTML 로 바꾼다")
    void rendersBasicSyntax() {
        String html = MarkdownRenderer.toSafeHtml("""
                # 제목
                **굵게** 와 *기울임*

                - 하나
                - 둘
                """);

        assertThat(html).contains("<h1>제목</h1>", "<strong>굵게</strong>", "<em>기울임</em>",
                "<li>하나</li>", "<li>둘</li>");
    }

    @Test
    @DisplayName("코드 블록을 유지한다")
    void keepsCodeBlocks() {
        String html = MarkdownRenderer.toSafeHtml("""
                ```java
                int a = 1;
                ```
                """);

        assertThat(html).contains("<pre>", "<code", "int a = 1;");
    }

    @Test
    @DisplayName("script 태그는 살아남지 못한다 - 렌더러는 원본 HTML 을 그대로 통과시킨다")
    void stripsScriptTag() {
        String html = MarkdownRenderer.toSafeHtml("안녕<script>alert('xss')</script>");

        assertThat(html).doesNotContain("<script").doesNotContain("alert");
        assertThat(html).contains("안녕");
    }

    @Test
    @DisplayName("이벤트 핸들러 속성을 떼어 낸다")
    void stripsEventHandlers() {
        String html = MarkdownRenderer.toSafeHtml("<img src=\"http://x/y.png\" onerror=\"alert(1)\">");

        assertThat(html).doesNotContain("onerror");
    }

    @Test
    @DisplayName("javascript: 로 시작하는 링크는 통과하지 못한다")
    void stripsJavascriptScheme() {
        String html = MarkdownRenderer.toSafeHtml("[누르지 마세요](javascript:alert(1))");

        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("iframe·style 처럼 허용 목록에 없는 태그는 남지 않는다")
    void stripsTagsOutsideSafelist() {
        String html = MarkdownRenderer.toSafeHtml(
                "<iframe src=\"http://evil\"></iframe><style>body{display:none}</style>");

        assertThat(html).doesNotContain("<iframe").doesNotContain("<style");
    }

    @Test
    @DisplayName("정상 링크는 새 창으로 열고 원래 창을 조작하지 못하게 한다")
    void externalLinksOpenSafely() {
        String html = MarkdownRenderer.toSafeHtml("[공고](https://example.com)");

        assertThat(html).contains("href=\"https://example.com\"");
        assertThat(html).contains("target=\"_blank\"");
        assertThat(html).contains("noopener");
    }

    @Test
    @DisplayName("표를 그릴 수 있다 (후기 글에서 자주 쓴다)")
    void keepsTableMarkup() {
        String html = MarkdownRenderer.toSafeHtml("<table><tr><td>칸</td></tr></table>");

        assertThat(html).contains("<table>", "<td>칸</td>");
    }

    @Test
    @DisplayName("빈 값은 빈 문자열로 다룬다")
    void handlesBlankInput() {
        assertThat(MarkdownRenderer.toSafeHtml(null)).isEmpty();
        assertThat(MarkdownRenderer.toSafeHtml("   ")).isEmpty();
    }

    @Test
    @DisplayName("목록 미리보기는 마크다운 기호를 걷어 낸 평문이다")
    void previewIsPlainText() {
        String preview = MarkdownRenderer.toPlainPreview("# 제목\n**굵은** 본문입니다", 100);

        assertThat(preview).doesNotContain("#", "**", "<");
        assertThat(preview).contains("제목", "굵은 본문입니다");
    }

    @Test
    @DisplayName("미리보기는 정해진 길이에서 자른다")
    void previewIsTruncated() {
        String preview = MarkdownRenderer.toPlainPreview("가".repeat(50), 10);

        assertThat(preview).isEqualTo("가".repeat(10) + "…");
    }

    @Test
    @DisplayName("미리보기에도 스크립트가 새어 나오지 않는다")
    void previewIsAlsoSanitized() {
        String preview = MarkdownRenderer.toPlainPreview("<script>alert(1)</script>본문", 100);

        assertThat(preview).doesNotContain("alert").contains("본문");
    }
}
