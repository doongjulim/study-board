package com.example.board.common.markdown;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 마크다운을 화면에 넣어도 안전한 HTML 로 바꾼다.
 *
 * <p>두 단계로 나뉘는 것이 중요하다. commonmark 는 <b>원본에 섞인 HTML 을 그대로 통과시킨다</b> -
 * 규격이 그렇게 정하고 있기 때문이다. 따라서 렌더링 결과를 그대로 화면에 넣으면
 * 글 본문에 {@code <script>} 한 줄만 적어도 다른 사람 브라우저에서 실행된다.
 * 렌더링 뒤에 반드시 살균(jsoup)을 거친다.</p>
 *
 * <p>허용 목록 방식을 쓰는 이유는, 막을 것을 나열하는 방식(블랙리스트)은
 * 새 공격 기법이 나올 때마다 뚫리기 때문이다. 여기 적힌 태그만 살아남는다.</p>
 *
 * <p>결과는 저장하지 않고 읽을 때마다 만든다. 저장해 두면 이 정책을 고쳤을 때
 * 이미 저장된 글에는 적용되지 않아, 과거의 허술한 규칙이 영원히 남는다.</p>
 */
public final class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    /**
     * 살아남는 태그 목록.
     *
     * <p>basic 에는 링크·강조·목록·인용·코드가 들어 있다. 여기에 제목과 표를 더한다.
     * 이미지는 basicWithImages 로 열되 jsoup 이 http/https 만 통과시키므로
     * {@code javascript:} 나 {@code data:} 로 시작하는 주소는 자동으로 떨어진다.</p>
     */
    private static final Safelist SAFELIST = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "hr",
                    "table", "thead", "tbody", "tr", "th", "td", "del", "s")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            // 코드 블록의 언어 표시(class="language-java")는 하이라이팅에 쓰이므로 남긴다
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            // 링크는 새 창으로 열되, 원래 창을 조작하지 못하게 한다 (탭내빙)
            .addAttributes("a", "rel", "target");

    private MarkdownRenderer() {
    }

    public static String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node parsed = PARSER.parse(markdown);
        String rendered = RENDERER.render(parsed);

        Document sanitized = Jsoup.parse(Jsoup.clean(rendered, SAFELIST));
        sanitized.outputSettings().prettyPrint(false); // 코드 블록의 줄바꿈·들여쓰기를 건드리지 않는다
        // 외부 링크는 새 창으로, 그리고 opener 를 끊는다
        sanitized.select("a[href]").forEach(link -> {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer nofollow");
        });
        return sanitized.body().html();
    }

    /**
     * 목록 화면의 미리보기용 평문. 마크다운 기호가 그대로 보이면 목록이 지저분해진다.
     *
     * @param limit 잘라 낼 글자 수
     */
    public static String toPlainPreview(String markdown, int limit) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(toSafeHtml(markdown)).text().strip();
        return (text.length() <= limit) ? text : text.substring(0, limit) + "…";
    }
}
