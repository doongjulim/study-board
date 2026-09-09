package com.example.board.auth.oauth;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.service.TokenService;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * 소셜 로그인이 끝난 자리.
 *
 * <p>여기서 <b>우리 방식의 로그인으로 갈아탄다</b> - 이 갈아타기가 빠지면 이후의 모든 코드가
 * "이 사람이 어디로 들어왔는지" 를 알아야 한다. 그래서 확인할 것은 두 가지다:
 * 제공자가 준 신원으로 회원을 찾거나 만들었는가, 그리고 평소와 같은 JWT 쿠키가 나갔는가.</p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock MemberService memberService;
    @Mock TokenService tokenService;

    private final AuthCookies authCookies = new AuthCookies(new CookiePolicy(false), 15, 14);

    private OAuth2LoginSuccessHandler handler() {
        return new OAuth2LoginSuccessHandler(memberService, tokenService, authCookies);
    }

    private OAuth2AuthenticationToken token(String registrationId, Map<String, Object> attributes,
                                            String nameAttributeKey) {
        return new OAuth2AuthenticationToken(
                new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"),
                        attributes, nameAttributeKey),
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                registrationId);
    }

    private void givenTokensIssued(Member member) {
        given(tokenService.issueFor(member))
                .willReturn(new TokenService.TokenPair("access-token", "refresh-token", null));
    }

    @Test
    @DisplayName("구글 응답의 sub·email·name 으로 회원을 찾거나 만든다")
    void createsMemberFromGoogleAttributes() throws IOException {
        Member member = new Member("google_1234", "!social", "동주", "dj@example.com");
        given(memberService.findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주"))
                .willReturn(member);
        givenTokensIssued(member);

        handler().onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                token("google", Map.of("sub", "1234", "email", "dj@example.com", "name", "동주"), "sub"));

        then(memberService).should().findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주");
    }

    @Test
    @DisplayName("카카오는 두 겹 안에 든 값을 꺼내 쓴다")
    void readsNestedKakaoAttributes() throws IOException {
        Member member = new Member("kakao_9999", "!social", "동주", null);
        given(memberService.findOrCreateOAuthMember("kakao", "9999", null, "동주")).willReturn(member);
        givenTokensIssued(member);

        handler().onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                token("kakao", Map.of("id", "9999",
                        "kakao_account", Map.of("profile", Map.of("nickname", "동주"))), "id"));

        then(memberService).should().findOrCreateOAuthMember("kakao", "9999", null, "동주");
    }

    @Test
    @DisplayName("닉네임을 주지 않으면 제공자 이름으로 대신 만든다 - 화면에 빈칸이 남으면 안 된다")
    void fallsBackWhenNicknameMissing() throws IOException {
        Member member = new Member("kakao_1", "!social", "kakao 사용자", null);
        given(memberService.findOrCreateOAuthMember("kakao", "1", null, "kakao 사용자")).willReturn(member);
        givenTokensIssued(member);

        handler().onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                token("kakao", Map.of("id", "1"), "id"));

        then(memberService).should().findOrCreateOAuthMember("kakao", "1", null, "kakao 사용자");
    }

    @Test
    @DisplayName("평소 로그인과 똑같이 JWT 쿠키를 내려보내고 홈으로 보낸다")
    void issuesOurOwnCookies() throws IOException {
        Member member = new Member("google_1234", "!social", "동주", "dj@example.com");
        given(memberService.findOrCreateOAuthMember(any(), any(), any(), any())).willReturn(member);
        givenTokensIssued(member);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler().onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("google", Map.of("sub", "1234", "email", "dj@example.com", "name", "동주"), "sub"));

        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.startsWith("ACCESS_TOKEN=access-token"))
                .anyMatch(header -> header.startsWith("REFRESH_TOKEN=refresh-token"));
        // 첫 로그인이면 홈이 온보딩으로 다시 보낸다 - 그 판단은 진입점 한 곳에만 둔다
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }
}
