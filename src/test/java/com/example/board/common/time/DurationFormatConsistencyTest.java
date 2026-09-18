package com.example.board.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분을 시간으로 적는 방식은 {@link ReadableDuration} 한 곳만 안다.
 *
 * <p>― 왜 이런 테스트가 필요한가<br>
 * 이 앱은 같은 값을 두 가지로 적고 있었다. 순위표와 챌린지는 {@code ReadableDuration} 을 써서
 * "45분"·"1시간" 이라고 적었는데, 통계·대시보드·추이 차트·주간 리포트는 시(hour)와 분을
 * 각자 이어 붙여 <b>"0시간 45분"·"1시간 0분"</b> 이라고 적었다. 사람이 쓰지 않는 말이고,
 * 화면을 옮길 때마다 같은 숫자가 다르게 보였다.
 *
 * <p>날짜 포맷이 열두 가지로 갈라졌던 것과 같은 일이다({@code DateFormatConsistencyTest}).
 * 규칙이 템플릿과 문자열 안에 흩어져 있으면 상수로 묶을 수 없으므로, 늘어나는 것을 막는 방법은
 * 이렇게 훑어보는 것뿐이다. 막자는 게 아니라 <b>모르는 사이에 갈라지지 않게</b> 하자는 것이다.
 */
class DurationFormatConsistencyTest {

    /**
     * 시(hour)와 분을 <b>나란히 이어 붙이는</b> 자리만 찾는다.
     *
     * <p>"이 링크는 %d시간 동안 유효합니다" 처럼 시간만 적는 문장은 대상이 아니다 -
     * 여기서 막으려는 것은 분이 0일 때 "1시간 0분" 이 되는 <b>조합</b>이지 '시간' 이라는 낱말이 아니다.</p>
     */
    private static final Pattern HAND_ASSEMBLED = Pattern.compile(
            "%d시간\\s*%d분"                  // "총 공부 시간 %d시간 %d분"
                    + "|'시간 '\\s*\\+"         // ... + '시간 ' + ... + '분'
                    + "|\"시간 \"\\s*\\+"
                    + "|시간\\s*[*$]\\{[^}]*\\}\\s*분");  // |...{...}시간 *{...}분|
    // 뒤에 '분' 이 따라오는 것까지 본다 - "예상 소요 시간 ${...}" 같은 문장이 걸리면
    // 이 테스트는 지키려던 규칙 대신 낱말을 막게 된다.

    /** 이 파일들만 시·분 표기를 직접 다룰 수 있다 */
    private static final List<String> SOURCE_OF_TRUTH = List.of(
            "ReadableDuration.java", "DurationFormatConsistencyTest.java");

    @Test
    @DisplayName("화면과 알림 문구는 시·분을 직접 잇지 않는다 - 표기 규칙은 ReadableDuration 하나가 정한다")
    void noHandAssembledDurations() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/resources/templates"), Path.of("src/main/java"))) {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.filter(DurationFormatConsistencyTest::isSource).toList()) {
                    if (SOURCE_OF_TRUTH.contains(file.getFileName().toString())) {
                        continue;
                    }
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = HAND_ASSEMBLED.matcher(stripComments(text));
                    if (matcher.find()) {
                        offenders.add(file + " → " + matcher.group());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("시·분을 직접 이어 붙인 자리가 있다. ReadableDuration.of(분) 을 쓰면 된다")
                .isEmpty();
    }

    private static boolean isSource(Path path) {
        String name = path.toString();
        return name.endsWith(".html") || name.endsWith(".java");
    }

    /**
     * 주석은 검사하지 않는다 - 결함을 설명하는 글에 그 결함의 모양이 나오는 것은 자연스럽고,
     * 그것까지 막으면 "왜 이렇게 했는지" 를 적을 수 없게 된다.
     */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?m)^\\s*//.*$", "");
    }
}
