package com.example.board.auth.jwt;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 쿠키의 액세스 토큰(JWT)을 검증해 SecurityContext 에 인증을 채운다.
 * 액세스 토큰이 없거나 만료됐으면 리프레시 토큰으로 자동 재발급을 시도한다
 * (SSR 이라 링크 이동마다 클라이언트가 갱신 요청을 보낼 수 없으므로 필터에서 처리).
 * 둘 다 유효하지 않으면 익명으로 통과시킨다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;
    private final AuthCookies authCookies;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<MemberPrincipal> principal = authCookies.readAccessToken(request)
                .flatMap(tokenProvider::parse);

        if (principal.isEmpty()) {
            principal = refresh(request, response);
        }
        principal.ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    /** 리프레시 토큰이 살아 있으면 토큰 쌍을 재발급해 쿠키로 내려준다 */
    private Optional<MemberPrincipal> refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> rawRefreshToken = authCookies.readRefreshToken(request);
        if (rawRefreshToken.isEmpty()) {
            return Optional.empty();
        }
        Optional<TokenService.TokenPair> reissued = tokenService.refresh(rawRefreshToken.get());
        if (reissued.isEmpty()) {
            authCookies.clear(response); // 폐기·만료된 토큰이면 쿠키도 정리한다
            return Optional.empty();
        }
        authCookies.write(response, reissued.get());
        return Optional.of(reissued.get().principal());
    }

    private void authenticate(MemberPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
