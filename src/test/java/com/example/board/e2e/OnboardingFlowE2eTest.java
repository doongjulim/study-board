package com.example.board.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 처음 온 사람이 가입해서 첫 계획을 적기까지.
 *
 * <p>이 경로는 화면 여섯 개와 폼 네 개를 지난다. 각 조각은 이미 단위 테스트가 있지만,
 * <b>이어 붙였을 때</b> 실제로 통하는지는 여기서만 확인된다 -
 * 폼에 CSRF 토큰이 박히는지, 로그인 쿠키가 다음 요청에 실려 가는지,
 * "가입 직후에는 온보딩으로" 라는 분기가 브라우저에서 정말 일어나는지.</p>
 */
@DisplayName("[E2E] 가입부터 첫 계획까지")
class OnboardingFlowE2eTest extends E2eSupport {

    @Test
    @DisplayName("가입 → 로그인 → 온보딩 → 대시보드 → 첫 계획 등록이 한 흐름으로 이어진다")
    void signupThroughFirstPlan() {
        String suffix = uniqueSuffix();
        String loginId = "e2e" + suffix;
        String nickname = "이이" + suffix;
        String password = "password123!";

        // ── 가입 ────────────────────────────────────────────
        page.navigate(url("/signup"));
        page.fill("#loginId", loginId);
        page.fill("#password", password);
        page.fill("#passwordConfirm", password);
        page.fill("#nickname", nickname);
        button("가입하기").click();

        // 가입은 자동 로그인이 아니다 - 로그인 화면으로 보낸다
        assertThat(page).hasURL(url("/login"));

        // ── 로그인 ──────────────────────────────────────────
        page.fill("#loginId", loginId);
        page.fill("#password", password);
        button("로그인").click();

        // 아직 첫 사용 안내를 마치지 않았으므로 대시보드가 아니라 온보딩으로 간다
        assertThat(page).hasURL(url("/onboarding"));
        assertThat(page.locator(".dash-greeting")).containsText(nickname);

        // ── 온보딩 건너뛰기 ────────────────────────────────
        // 건너뛰어도 '마친 것' 으로 기록되어야 한다. 안 그러면 다시 붙잡히게 된다
        button("건너뛰고 둘러보기").click();

        assertThat(page).hasURL(url("/"));
        assertThat(page.locator(".dash-greeting")).containsText(nickname);

        // 다시 들어와도 온보딩으로 되돌아가지 않는다
        page.navigate(url("/"));
        assertThat(page).hasURL(url("/"));

        // ── 첫 계획 등록 ───────────────────────────────────
        // 한 줄 추가는 화면을 넘기지 않는다 - plans.js 가 폼 제출을 가로채 fetch 로 보내고
        // 돌아온 값으로 그 자리에 줄을 붙인다. 그래서 "주소가 바뀌었는가" 가 아니라
        // "목록에 줄이 생겼는가" 로 확인해야 한다. 주소로 확인하면 JS 를 껐을 때만 통과한다
        page.navigate(url("/plans/daily"));
        page.fill(".quick-title", "이커머스 스터디 첫 모임");
        button("추가").click();

        assertThat(page.locator("#plan-list .plan-title").first())
                .containsText("이커머스 스터디 첫 모임");
        assertThat(page).hasURL(url("/plans/daily"));
    }

    @Test
    @DisplayName("로그인하지 않으면 플래너 대신 로그인 화면으로 보낸다")
    void plannerRequiresLogin() {
        page.navigate(url("/plans/daily"));

        assertThat(page).hasURL(Pattern.compile(".*/login.*"));
    }

    @Test
    @DisplayName("처음 온 사람에게는 소개 화면을 보여 준다 - 로그인 화면으로 튕기지 않는다")
    void anonymousSeesLanding() {
        page.navigate(url("/"));

        assertThat(page).hasURL(url("/"));
        assertThat(page.locator(".hero-title")).isVisible();
    }
}
