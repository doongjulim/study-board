package com.example.board.common.web;

import com.example.board.common.markdown.MarkdownRenderer;
import org.springframework.ui.Model;

/**
 * 공유 미리보기(Open Graph) 값을 화면에 실어 준다.
 *
 * <p>― 왜 모델 속성인가<br>
 * 레이아웃 프래그먼트({@code fragments/layout :: page})는 스무 개가 넘는 화면이 함께 쓴다.
 * 인자를 늘리면 그 전부를 고쳐야 하고, 대부분의 화면은 기본값으로 충분하다.
 * 그래서 <b>필요한 화면만</b> 이 값을 넣고, 레이아웃은 없으면 기본값을 쓴다.
 *
 * <p>키 이름을 여기 한 곳에만 두는 이유는 오타가 조용히 지나가기 때문이다 -
 * {@code ogDesciption} 이라고 적어도 화면은 멀쩡히 뜨고, 잘못된 것은 <b>남의 메신저</b>에서만 보인다.
 */
public final class OpenGraphMeta {

    /** 미리보기 설명의 길이. 대부분의 메신저가 두어 줄만 보여 주므로 그보다 길 이유가 없다 */
    public static final int DESCRIPTION_LENGTH = 140;

    private OpenGraphMeta() {
    }

    /** 마크다운 본문에서 설명을 뽑아 싣는다 (글·플랜 상세) */
    public static void applyFromMarkdown(Model model, String title, String markdown) {
        apply(model, title, MarkdownRenderer.toPlainPreview(markdown, DESCRIPTION_LENGTH), "article");
    }

    public static void apply(Model model, String title, String description, String type) {
        model.addAttribute("ogTitle", title);
        // 본문이 비어 있으면 넣지 않는다 - 빈 설명은 기본 설명보다 나쁘다(레이아웃이 기본값으로 되돌린다)
        if (description != null && !description.isBlank()) {
            model.addAttribute("ogDescription", description);
        }
        model.addAttribute("ogType", type);
    }
}
