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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * 좁은 화면에서 모든 메뉴에 닿을 수 있는가.
 *
 * <p>헤더 메뉴는 720px 이하에서 감춰지고 하단 탭바가 대신한다. 그런데 헤더는 일곱 칸이고
 * 탭바는 다섯 칸이라, 넘치는 것을 그냥 빼 두었더니 <b>D-Day·그룹·공유 플랜으로 가는 길이 사라졌다.</b>
 * 그룹은 본문 어디에도 링크가 없어 폰에서는 영영 닿을 수 없었고, D-Day 는 홈의 칩으로만 갈 수 있었는데
 * 그 칩은 등록된 것이 있을 때만 그려져서 <b>처음 쓰는 사람일수록 못 가는</b> 구조였다.</p>
 *
 * <p>탭바는 헤더 프래그먼트에 있어 모든 화면이 함께 쓴다. 그래서 아무 화면 하나로 확인하면 된다.</p>
 */
@WebMvcTest(DdayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        AuthCookies.class, CookiePolicy.class, TestClockConfig.class})
class MobileNavigationTest {

    @Autowired MockMvc mockMvc;
    @MockBean DdayService ddayService;
    @MockBean TokenService tokenService;

    private RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(7L, "dongju", "동주"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    @DisplayName("탭바에서 헤더의 모든 메뉴에 닿을 수 있다")
    void tabbarReachesEveryMenu() throws Exception {
        var result = mockMvc.perform(get("/ddays").with(memberAuth()))
                .andExpect(content().string(containsString("tabbar")));

        // 탭바에 직접 놓인 넷
        for (String href : List.of("/plans/daily", "/stats", "/posts")) {
            result.andExpect(content().string(containsString("href=\"" + href + "\"")));
        }
        // '더보기' 에 담긴 넷 - 예전에는 이 셋으로 가는 길이 폰에 아예 없었다
        for (String href : List.of("/ddays", "/groups", "/plans/shared", "/me")) {
            result.andExpect(content().string(containsString("href=\"" + href + "\"")));
        }
        result.andExpect(content().string(containsString("tab-more")));
    }
}
