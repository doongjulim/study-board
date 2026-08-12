package com.example.board.auth.service;

import com.example.board.auth.TokenHasher;
import com.example.board.auth.domain.RefreshToken;
import com.example.board.auth.repository.RefreshTokenRepository;
import com.example.board.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 리프레시 토큰 발급·회전·폐기.
 * 액세스 토큰(무상태 JWT)과 달리 DB 에 상태를 두기 때문에 로그아웃 즉시 무효화할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-days}")
    private long refreshTokenDays;

    /** 새 리프레시 토큰을 발급하고 원문을 반환한다 (DB 에는 해시만 저장) */
    @Transactional
    public String issue(Member member) {
        String rawToken = TokenHasher.newToken();
        refreshTokenRepository.save(new RefreshToken(member, TokenHasher.hash(rawToken),
                LocalDateTime.now().plus(Duration.ofDays(refreshTokenDays))));
        return rawToken;
    }

    /**
     * 토큰을 검증하고 새 토큰으로 교체한다(회전).
     * 유효하지 않거나 만료됐으면 빈 Optional 을 반환하고, 만료된 토큰은 정리한다.
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken) {
        return refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .flatMap(stored -> {
                    refreshTokenRepository.delete(stored); // 한 번 쓴 토큰은 항상 폐기한다
                    if (stored.isExpired(LocalDateTime.now())) {
                        return Optional.empty();
                    }
                    Member member = stored.getMember();
                    return Optional.of(new Rotation(member, issue(member)));
                });
    }

    /** 로그아웃 - 해당 토큰을 DB 에서 지워 즉시 무효화한다 */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(TokenHasher.hash(rawToken));
    }

    /**
     * 회원의 모든 리프레시 토큰을 폐기한다 (전 기기 로그아웃).
     * 비밀번호를 바꾸는 이유는 대개 계정이 털렸다고 의심할 때이므로,
     * 다른 기기에 남아 있는 세션도 함께 끊어야 의미가 있다.
     */
    @Transactional
    public void revokeAll(Long memberId) {
        refreshTokenRepository.deleteByMember_Id(memberId);
    }

    @Transactional
    public int deleteExpired(LocalDateTime now) {
        return refreshTokenRepository.deleteByExpiresAtBefore(now);
    }


    /** 회전 결과 - 토큰 주인과 새로 발급된 리프레시 토큰 원문 */
    public record Rotation(Member member, String newRawToken) {
    }
}
