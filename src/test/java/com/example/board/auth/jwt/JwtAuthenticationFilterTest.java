package com.example.board.auth.jwt;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-secret-key-must-be-32-bytes-min!";

    @Mock TokenService tokenService;

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 15);
    private final AuthCookies authCookies = new AuthCookies(15, 14);

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(tokenProvider, tokenService, authCookies);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MemberPrincipal authenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : (MemberPrincipal) authentication.getPrincipal();
    }

    @Test
    @DisplayName("유효한 액세스 토큰이면 인증을 채우고 갱신을 시도하지 않는다")
    void validAccessToken() throws Exception {
        String token = tokenProvider.createToken(1L, "tester1", "테스터");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, token));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertThat(authenticatedPrincipal()).isEqualTo(new MemberPrincipal(1L, "tester1", "테스터"));
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("액세스 토큰이 만료됐어도 리프레시 토큰이 살아 있으면 재발급하고 인증한다")
    void expiredAccessToken_refreshed() throws Exception {
        String expiredToken = new JwtTokenProvider(SECRET, 0).createToken(1L, "tester1", "테스터");
        MemberPrincipal principal = new MemberPrincipal(1L, "tester1", "테스터");
        given(tokenService.refresh("refresh-value"))
                .willReturn(Optional.of(new TokenService.TokenPair("new-access", "new-refresh", principal)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.ACCESS_TOKEN, expiredToken),
                new Cookie(AuthCookies.REFRESH_TOKEN, "refresh-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertThat(authenticatedPrincipal()).isEqualTo(principal);
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith("ACCESS_TOKEN=new-access"))
                .anyMatch(cookie -> cookie.startsWith("REFRESH_TOKEN=new-refresh"));
    }

    @Test
    @DisplayName("폐기된(로그아웃된) 리프레시 토큰이면 익명으로 통과시키고 쿠키를 지운다")
    void revokedRefreshToken_clearsCookies() throws Exception {
        given(tokenService.refresh("revoked-value")).willReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookies.REFRESH_TOKEN, "revoked-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertThat(authenticatedPrincipal()).isNull();
        assertThat(response.getHeaders("Set-Cookie"))
                .allMatch(cookie -> cookie.contains("Max-Age=0"))
                .hasSize(2);
    }

    @Test
    @DisplayName("쿠키가 전혀 없으면 익명으로 통과시킨다")
    void noCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        assertThat(authenticatedPrincipal()).isNull();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }
}
