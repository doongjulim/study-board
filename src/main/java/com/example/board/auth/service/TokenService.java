package com.example.board.auth.service;

import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 액세스 토큰(JWT)과 리프레시 토큰(DB)을 함께 다루는 진입점 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;

    /** 로그인 성공 시 두 토큰을 한 번에 발급한다 */
    public TokenPair issueFor(Member member) {
        return toPair(member, refreshTokenService.issue(member));
    }

    /**
     * 리프레시 토큰으로 액세스 토큰을 재발급한다.
     * 리프레시 토큰도 함께 회전하므로, 폐기(로그아웃)된 토큰으로는 재발급되지 않는다.
     */
    public Optional<TokenPair> refresh(String rawRefreshToken) {
        return refreshTokenService.rotate(rawRefreshToken)
                .map(rotation -> toPair(rotation.member(), rotation.newRawToken()));
    }

    public void revoke(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenPair toPair(Member member, String refreshToken) {
        String accessToken = tokenProvider.createToken(
                member.getId(), member.getLoginId(), member.getNickname());
        MemberPrincipal principal =
                new MemberPrincipal(member.getId(), member.getLoginId(), member.getNickname());
        return new TokenPair(accessToken, refreshToken, principal);
    }

    public record TokenPair(String accessToken, String refreshToken, MemberPrincipal principal) {
    }
}
