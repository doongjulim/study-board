package com.example.board.auth.oauth;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.service.TokenService;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인이 끝난 자리.
 *
 * <p>여기서 <b>우리 방식의 로그인으로 갈아탄다</b> - 제공자에게 확인받은 신원으로 회원을 찾거나 만들고,
 * 평소 로그인과 똑같이 JWT 쿠키를 발급한다. 그래서 로그인 이후의 모든 코드(필터·컨트롤러·화면)는
 * 이 사람이 어디로 들어왔는지 알 필요가 없다.</p>
 *
 * <p>Spring Security 의 세션에 인증을 남기지 않는다 - 이 애플리케이션은 무상태이고,
 * 세션 관리를 켜는 순간 CSRF 토큰이 요청마다 갈아 끼워진다(CLAUDE.md 의 '세션 관리' 함정).</p>
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberService memberService;
    private final TokenService tokenService;
    private final AuthCookies authCookies;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User oauthUser = token.getPrincipal();

        OAuthAttributes attributes = OAuthAttributes.of(registrationId, oauthUser.getAttributes());
        Member member = memberService.findOrCreateOAuthMember(
                registrationId,
                attributes.providerId(),
                attributes.email(),
                attributes.nicknameOr(registrationId + " 사용자"));

        authCookies.write(response, tokenService.issueFor(member));
        // 첫 로그인이면 홈이 온보딩으로 다시 보낸다 - 그 판단은 진입점 한 곳(HomeController)에만 둔다
        response.sendRedirect("/");
    }
}
