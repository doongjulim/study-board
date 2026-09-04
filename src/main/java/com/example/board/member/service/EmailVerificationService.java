package com.example.board.member.service;

import com.example.board.auth.TokenHasher;
import com.example.board.mail.LoggingMailSender;
import com.example.board.member.domain.EmailVerificationToken;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 이메일 인증.
 *
 * <p>주소가 실제로 닿는지 확인한다. 확인해 두지 않으면 오타 난 주소를 저장한 채로 지내다가
 * 비밀번호를 잊은 순간에야 알게 되고, 그때는 고칠 방법이 없다.</p>
 *
 * <p>토큰 규칙은 비밀번호 재설정과 같다 - 원문은 메일로만 나가고 DB 에는 SHA-256 해시만 남으며,
 * 한 번 쓰면 폐기된다. 재설정과 달리 "그런 주소 없음" 을 감출 이유는 없다.
 * 자기 계정의 주소를 자기가 인증하는 것이라 존재 여부가 새어 나갈 대상이 아니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final MemberService memberService;
    private final LoggingMailSender mailSender;
    private final Clock clock;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 인증 링크를 보낸다.
     *
     * @throws IllegalStateException 주소가 없거나 이미 인증된 경우 - 둘 다 사용자가 화면에서 알 수 있는 상태다
     */
    @Transactional
    public void sendVerificationLink(Long memberId) {
        Member member = memberService.findActive(memberId);
        if (member.getEmail() == null) {
            throw new IllegalStateException("먼저 이메일을 등록해 주세요.");
        }
        if (!member.needsEmailVerification()) {
            throw new IllegalStateException("이미 인증된 이메일입니다.");
        }

        tokenRepository.deleteByMember_Id(memberId); // 새 링크를 내면 이전 링크는 죽는다

        String rawToken = TokenHasher.newToken();
        tokenRepository.save(new EmailVerificationToken(
                member, member.getEmail(), TokenHasher.hash(rawToken), LocalDateTime.now(clock)));

        mailSender.send(member.getEmail(), "[스터디플랜] 이메일 인증",
                buildMailBody(member, rawToken));
    }

    /**
     * 링크를 눌렀을 때. 성공하면 토큰을 폐기한다.
     *
     * @return 인증에 성공했는가 (만료·이미 쓴 링크·그 사이 주소가 바뀐 경우 false)
     */
    @Transactional
    public boolean verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return tokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .filter(token -> !token.isExpired(LocalDateTime.now(clock)))
                // 메일을 보낸 뒤 주소를 또 바꿨다면, 이 링크는 지금 쓰지 않는 주소를 가리킨다
                .filter(token -> token.matches(token.getMember().getEmail()))
                .map(token -> {
                    token.getMember().markEmailVerified();
                    tokenRepository.delete(token);
                    return true;
                })
                .orElse(false);
    }

    /** 만료된 토큰 정리 - 스케줄러가 부른다 */
    @Transactional
    public int deleteExpired(LocalDateTime now) {
        return tokenRepository.deleteExpired(now);
    }

    private String buildMailBody(Member member, String rawToken) {
        return """
                %s님, 안녕하세요.

                아래 주소를 눌러 이메일을 인증해 주세요.
                %s/email/verify?token=%s

                이 링크는 %d시간 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
                """.formatted(member.getNickname(), baseUrl, rawToken,
                EmailVerificationToken.VALIDITY.toHours());
    }

    /** 화면이 "인증 메일을 보낼 수 있는 상태인가" 를 묻는 자리 */
    public Optional<String> pendingEmailOf(Long memberId) {
        Member member = memberService.findActive(memberId);
        return member.needsEmailVerification() ? Optional.of(member.getEmail()) : Optional.empty();
    }
}
