package com.example.board.auth.service;

import com.example.board.auth.TokenHasher;
import com.example.board.auth.domain.PasswordResetToken;
import com.example.board.auth.repository.PasswordResetTokenRepository;
import com.example.board.mail.LoggingMailSender;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 비밀번호 찾기.
 *
 * <p>가입한 이메일로 짧게 유효한 링크를 보내고, 그 링크로 새 비밀번호를 정하게 한다.
 * 토큰 원문은 메일에만 실리고 DB 에는 해시만 남는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final MemberService memberService;
    private final LoggingMailSender mailSender;
    private final Clock clock;

    /** 메일에 담을 링크의 앞부분 - 배포 주소에 맞춰 바꾼다 */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 재설정 링크를 보낸다.
     *
     * <p>가입되지 않은 이메일이어도 아무 일 없었던 것처럼 조용히 끝낸다.
     * "그런 계정 없음" 을 알려 주면 어떤 이메일이 가입돼 있는지 확인하는 수단이 되기 때문이다.
     * 화면은 어느 경우든 같은 안내를 보여 준다.</p>
     */
    @Transactional
    public void sendResetLink(String email) {
        memberService.findActiveByEmail(email).ifPresent(member -> {
            // 새 링크를 내면 이전 링크는 무효가 되어야 한다
            tokenRepository.deleteByMember_Id(member.getId());

            String rawToken = TokenHasher.newToken();
            tokenRepository.save(new PasswordResetToken(
                    member, TokenHasher.hash(rawToken), LocalDateTime.now(clock)));

            mailSender.send(member.getEmail(), "[스터디플랜] 비밀번호 재설정 안내",
                    buildMailBody(member, rawToken));
        });
    }

    /** 링크를 열었을 때 새 비밀번호 입력 화면을 보여 줘도 되는지 확인한다 */
    public boolean isUsable(String rawToken) {
        return findValid(rawToken).isPresent();
    }

    /**
     * 새 비밀번호를 설정한다. 성공하면 토큰을 즉시 폐기한다.
     * 유효하지 않은 토큰이면 false 를 반환한다(만료되었거나 이미 쓴 링크).
     */
    @Transactional
    public boolean reset(String rawToken, String newPassword) {
        return findValid(rawToken)
                .map(token -> {
                    memberService.resetPassword(token.getMember().getId(), newPassword);
                    tokenRepository.delete(token); // 한 번 쓴 링크는 다시 열리지 않는다
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int deleteExpired(LocalDateTime now) {
        return tokenRepository.deleteByExpiresAtBefore(now);
    }

    private Optional<PasswordResetToken> findValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .filter(token -> !token.isExpired(LocalDateTime.now(clock)));
    }

    private String buildMailBody(Member member, String rawToken) {
        return """
                %s님, 안녕하세요.

                아래 링크에서 새 비밀번호를 설정할 수 있습니다.
                %s/password/reset?token=%s

                이 링크는 %d분 동안만 유효합니다.
                본인이 요청한 것이 아니라면 이 메일을 무시하셔도 됩니다.
                """.formatted(member.getNickname(), baseUrl, rawToken,
                PasswordResetToken.VALIDITY.toMinutes());
    }
}
