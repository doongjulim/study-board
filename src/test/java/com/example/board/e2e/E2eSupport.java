package com.example.board.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.HttpHeader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 실제 브라우저로 도는 테스트의 공통 바탕.
 *
 * <p>단위 테스트와 {@code @WebMvcTest} 는 서버 안쪽만 본다. 그래서 CSRF 토큰이 폼에 실제로 박히는지,
 * 로그인 쿠키가 다음 요청에 실려 가는지, 리다이렉트가 브라우저에서 이어지는지는 확인되지 않는다 -
 * MockMvc 는 그 셋을 전부 우리가 손으로 만들어 주기 때문이다. 여기서만 잡히는 것이 그것들이다.</p>
 *
 * <p>테스트마다 새 {@link BrowserContext} 를 연다. 컨텍스트가 쿠키의 경계라,
 * 앞 테스트에서 로그인한 상태가 다음 테스트로 새지 않는다.
 * 브라우저 자체는 뜨는 데 시간이 걸려 클래스마다 한 번만 띄운다.</p>
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class E2eSupport {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    protected BrowserContext context;
    protected Page page;

    /** 응답을 받은 그 자리에서 본문·헤더를 읽으면 이벤트 스레드가 막힐 수 있어, 모아 두었다가 테스트가 끝난 뒤 읽는다 */
    private final List<Response> responses = new ArrayList<>();
    private final List<Response> failedResponses = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext();
        page = context.newPage();
        responses.clear();
        failedResponses.clear();
        watchForTrouble();
    }

    @AfterEach
    void closePage() {
        reportTrouble();
        if (context != null) {
            context.close();
        }
    }

    /**
     * 브라우저 쪽에서 벌어진 일을 기록해 둔다.
     *
     * <p>E2E 가 깨졌을 때 기본 메시지는 "주소가 예상과 다르다" 뿐이라, 서버가 403 을 줬는지
     * 500 을 줬는지, 스크립트가 터졌는지가 드러나지 않는다. 원인을 알아내려고 매번 사람이
     * 브라우저를 다시 띄우게 되는데, 그 정보는 실패한 그 실행이 이미 손에 쥐고 있던 것이다.</p>
     *
     * <p>대화상자를 닫아 주는 것은 편의가 아니라 필수다 - {@code alert} 이 열려 있으면
     * 브라우저가 모든 명령에 응답을 멈춰, 테스트가 실패가 아니라 <b>정지</b>한다.</p>
     */
    private void watchForTrouble() {
        page.onDialog(dialog -> {
            System.out.println("[E2E] 대화상자(" + dialog.type() + "): " + dialog.message());
            dialog.dismiss();
        });
        page.onPageError(error -> System.out.println("[E2E] 스크립트 오류: " + error));
        page.onResponse(response -> {
            responses.add(response);
            if (response.status() >= 400) {
                failedResponses.add(response);
            }
        });
    }

    private void reportTrouble() {
        if (failedResponses.isEmpty()) {
            return;
        }
        for (Response response : failedResponses) {
            System.out.println("[E2E] " + response.status() + " "
                    + response.request().method() + " " + response.url());
            System.out.println("[E2E]   보낸 값: " + response.request().postData());
            System.out.println("[E2E]   본문: " + summarize(response));
        }
        // 403 의 대부분은 CSRF 다. 그런데 "토큰이 어긋났다" 는 어긋나게 만든 앞선 응답이 있어야 생긴다.
        // 어떤 응답이 토큰을 새로 내려 줬는지 보려면 주고받은 순서를 통째로 봐야 한다
        System.out.println("[E2E] --- 주고받은 순서 ---");
        for (Response response : responses) {
            System.out.println("[E2E] " + response.status() + " "
                    + response.request().method() + " " + path(response.url())
                    + setCookieOf(response));
        }
        System.out.println("[E2E] --- 끝난 뒤 쿠키 ---");
        context.cookies().forEach(cookie ->
                System.out.println("[E2E] " + cookie.name + "=" + abbreviate(cookie.value)));
    }

    /** 임의 포트가 매번 달라 주소 전체를 찍으면 눈으로 비교하기 어렵다 */
    private static String path(String url) {
        int slash = url.indexOf('/', url.indexOf("//") + 2);
        return slash < 0 ? url : url.substring(slash);
    }

    /**
     * 응답이 내려보낸 쿠키 이름들.
     *
     * <p>{@code headers()} 로는 보이지 않는다 - Playwright 가 거기서 Set-Cookie 를 빼기 때문이다.
     * 원본 헤더를 주는 {@code headersArray()} 를 써야 한다. 이걸 몰라 한 번 헛돌았다.</p>
     */
    private static String setCookieOf(Response response) {
        StringBuilder names = new StringBuilder();
        for (HttpHeader header : response.headersArray()) {
            if ("set-cookie".equalsIgnoreCase(header.name)) {
                // 이름만으로는 "같은 값을 다시 내려 준 것" 과 "새 값으로 갈아 끼운 것" 이 구분되지 않는다
                String[] pair = header.value.split("=", 2);
                names.append(names.isEmpty() ? "  ← Set-Cookie: " : ", ")
                        .append(pair[0]).append('=')
                        .append(pair.length > 1 ? head(pair[1]) : "");
            }
        }
        return names.toString();
    }

    private static String head(String cookieValue) {
        String value = cookieValue.split(";", 2)[0];
        return value.length() <= 10 ? value : value.substring(0, 10) + "…";
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 24) {
            return value;
        }
        return value.substring(0, 24) + "…(" + value.length() + "자)";
    }

    /** 오류 페이지에서 사람이 읽을 문장만 남긴다 - HTML 전체를 찍으면 정작 원인이 묻힌다 */
    private static String summarize(Response response) {
        try {
            String text = response.text()
                    .replaceAll("(?s)<(script|style)\\b.*?</\\1>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return text.length() > 400 ? text.substring(0, 400) + "…" : text;
        } catch (RuntimeException e) {
            return "(본문을 읽지 못했다: " + e.getMessage() + ")";
        }
    }

    /** 임의 포트로 뜨므로 주소를 하드코딩할 수 없다 */
    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 화면에 보이는 글자로 버튼을 찾는다.
     *
     * <p>CSS 클래스가 아니라 역할과 이름으로 찾는 이유는, 그것이 사용자가 버튼을 찾는 방식이기 때문이다.
     * 스타일을 바꿨다고 테스트가 깨지지 않고, 반대로 버튼 글자가 엉뚱하게 바뀌면 잡힌다.</p>
     */
    protected Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    /**
     * 테스트마다 다른 아이디를 쓴다.
     * 같은 아이디를 쓰면 두 번째 실행부터 "이미 사용 중인 아이디" 로 막혀,
     * 테스트가 처음 한 번만 통과하는 상태가 된다.
     */
    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
