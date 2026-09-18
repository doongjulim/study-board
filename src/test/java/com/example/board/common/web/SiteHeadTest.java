package com.example.board.common.web;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.dday.controller.DdayController;
import com.example.board.dday.service.DdayService;
import com.example.board.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 모든 화면의 <head> 에 들어가야 하는 것들.
 *
 * <p>아이콘과 공유 미리보기는 <b>화면 안에서는 아무 표도 나지 않는다.</b> 빠져 있어도 앱은 멀쩡히 돌고,
 * 잘못된 것은 탭 모서리와 <b>남의 메신저</b>에서만 보인다 - 그래서 아무도 알아차리지 못한 채 오래 남는다.
 * 한때 파비콘조차 없었다.</p>
 *
 * <p>레이아웃 프래그먼트는 모든 화면이 함께 쓰므로 아무 화면 하나로 확인하면 된다
 * ({@code MobileNavigationTest} 와 같은 이유).</p>
 */
@WebMvcTest(DdayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        AuthCookies.class, CookiePolicy.class, TestClockConfig.class})
class SiteHeadTest {

    @Autowired MockMvc mockMvc;
    @MockBean DdayService ddayService;
    @MockBean TokenService tokenService;

    private RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(7L, "dongju", "동주"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String head() throws Exception {
        return mockMvc.perform(get("/ddays").with(memberAuth()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("파비콘·홈 화면 아이콘·manifest 가 모두 선언되어 있다")
    void icons() throws Exception {
        String html = head();

        assertThat(html).contains("/favicon.svg");
        // SVG 를 못 읽는 브라우저가 내려올 자리
        assertThat(html).contains("/favicon.ico");
        // iOS 는 manifest 의 아이콘을 보지 않는다
        assertThat(html).contains("/icons/apple-touch-icon.png");
        assertThat(html).contains("/manifest.json");
        assertThat(html).contains("name=\"theme-color\"");
    }

    @Test
    @DisplayName("head 가 가리키는 아이콘·이미지는 로그인 없이 받을 수 있다")
    void headAssetsArePublic() throws Exception {
        // 브라우저는 파비콘을, 메신저는 og:image 를 로그인하지 않은 채 가져간다.
        // 막혀 있으면 로그인 화면으로 302 되고, 받는 쪽은 그것을 이미지로 읽으려다 실패한다.
        // 주소를 손으로 옮겨 적지 않고 실제로 그려진 head 에서 뽑는 이유는,
        // 아이콘을 하나 더 달면서 보안 설정만 빠뜨리는 것이 이 결함이 생긴 방식이기 때문이다.
        Matcher matcher = Pattern.compile("(?:href|content)=\"(?:http://localhost:8080)?(/(?:icons/|favicon|manifest)[^\"]*)\"")
                .matcher(head());

        List<String> assets = new ArrayList<>();
        while (matcher.find()) {
            assets.add(matcher.group(1));
        }
        assertThat(assets).as("head 에서 아이콘·manifest 주소를 하나도 찾지 못했다").isNotEmpty();

        for (String asset : assets) {
            assertThat(mockMvc.perform(get(asset)).andReturn().getResponse().getStatus())
                    .as("%s 가 로그인 없이 열리지 않는다", asset)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("공유 미리보기의 기본값이 채워져 있다 - 대부분의 화면은 따로 넣지 않는다")
    void openGraphDefaults() throws Exception {
        String html = head();

        assertThat(html).contains("property=\"og:title\"");
        assertThat(html).contains("property=\"og:description\"");
        assertThat(html).contains("property=\"og:type\"");
        assertThat(html).contains("name=\"description\"");
        // 미리보기를 만드는 쪽이 남의 서버라 이미지 주소는 절대 경로여야 한다
        assertThat(html).contains("content=\"http://localhost:8080/icons/og-default.png\"");
    }
}
