package com.example.board.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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
    }

    @AfterEach
    void closePage() {
        if (context != null) {
            context.close();
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
