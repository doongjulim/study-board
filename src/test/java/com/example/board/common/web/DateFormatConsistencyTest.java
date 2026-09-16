package com.example.board.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * 화면에 날짜를 적는 방식은 정해진 것만 쓴다.
 *
 * <p>한때 열두 가지가 있었다 - 하이픈(`yyyy-MM-dd`)·점(`yyyy.MM.dd`)·한글(`yyyy년 M월 d일`) 세 계열이
 * 섞여 있었고, {@code M/d(E)} 와 {@code M/d (E)} 처럼 <b>괄호 앞 공백만 다른 것</b>까지 공존했다.
 * 화면을 옮길 때마다 사용자가 날짜 읽는 법을 다시 익혀야 했다.</p>
 *
 * <p>포맷은 템플릿 안에 문자열로 흩어져 있어 늘어나는 것을 막을 방법이 이것뿐이다.
 * 새 화면을 만들다 무심코 한 가지를 더하면 여기서 걸린다 - 정말 새 용도가 필요하면
 * 이 표에 이유와 함께 추가하면 된다. 막자는 게 아니라 <b>모르는 사이에 늘지 않게</b> 하자는 것이다.</p>
 */
class DateFormatConsistencyTest {

    /** 용도 → 포맷. 여기 없는 포맷이 템플릿에 나오면 이 테스트가 실패한다 */
    private static final Map<String, String> ALLOWED = Map.of(
            "yyyy년 M월 d일 (E)", "화면 제목의 하루 (일간 뷰)",
            "M월 d일", "기간 제목의 양끝, 짧은 안내문",
            "yyyy-MM-dd", "요일이 의미 없는 기록 날짜 (가입일)",
            "yyyy-MM-dd (E)", "목록의 계획 날짜 (요일이 판단에 쓰인다)",
            "yyyy-MM-dd HH:mm", "작성·수정 시각",
            "M/d (E)", "좁은 칸 (주간 격자·주간 회고)",
            "M/d", "더 좁은 칸 (추이 차트 라벨 - 요일까지 넣으면 넘친다)",
            "HH:mm", "시각만",
            "yyyy-MM", "URL 파라미터 (화면에 보이지 않는다)");

    /** {@code #temporals.format(어떤값, '포맷')} 에서 포맷만 뽑는다 */
    private static final Pattern FORMAT_CALL =
            Pattern.compile("#temporals\\.format\\([^,()]*(?:\\([^()]*\\))?[^,()]*,\\s*'([^']*)'");

    @Test
    @DisplayName("템플릿의 날짜 포맷은 정해진 것뿐이다")
    void templatesUseOnlyAllowedFormats() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/templates"))) {
            List<Path> templates = paths.filter(p -> p.toString().endsWith(".html")).toList();
            for (Path template : templates) {
                Matcher matcher = FORMAT_CALL.matcher(Files.readString(template, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            }
        }

        assertThat(found)
                .as("정해지지 않은 날짜 포맷이 늘었다. 정말 필요하면 ALLOWED 에 용도와 함께 추가할 것")
                .isSubsetOf(ALLOWED.keySet());
    }

    @Test
    @DisplayName("괄호 앞 공백만 다른 짝이 생기지 않았다")
    void noNearDuplicateFormats() {
        // 'M/d(E)' 와 'M/d (E)' 가 함께 있던 적이 있다. 눈으로는 같아 보여 리뷰에서도 지나친다
        Set<String> squashed = new TreeSet<>();
        for (String format : ALLOWED.keySet()) {
            assertThat(squashed.add(format.replace(" ", "")))
                    .as("공백만 다른 포맷이 둘 있다: " + format)
                    .isTrue();
        }
    }
}
