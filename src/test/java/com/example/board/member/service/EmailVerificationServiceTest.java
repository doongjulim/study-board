package com.example.board.member.service;

import com.example.board.auth.TokenHasher;
import com.example.board.mail.LoggingMailSender;
import com.example.board.member.domain.EmailVerificationToken;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

/**
 * 이메일 인증.
 *
 * <p>비밀번호 재설정과 같은 토큰 규칙을 쓴다 - 원문은 메일로만 나가고 DB 에는 해시만,
 * 한 번 쓰면 폐기. 같은 규칙인데 검증이 한쪽(재설정)에만 있으면, 규칙이 갈라져도 아무도 모른다.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 10, 0);

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock MemberService memberService;
    @Mock LoggingMailSender mailSender;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        emailVerificationService = new EmailVerificationService(
                tokenRepository, memberService, mailSender, clock);
        ReflectionTestUtils.setField(emailVerificationService, "baseUrl", "http://localhost:8080");
    }

    private Member member(String email) {
        Member member = new Member("dongju", "encoded-password", "동주", email);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    // ── 발송 ──────────────────────────────────────────────────

    @Test
    @DisplayName("인증 메일에는 토큰 원문이 담기고, DB 에는 해시만 남는다")
    void sendsRawTokenButStoresOnlyHash() {
        given(memberService.findActive(MEMBER_ID)).willReturn(member("dj@example.com"));

        emailVerificationService.sendVerificationLink(MEMBER_ID);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailSender).should().send(eq("dj@example.com"), anyString(), body.capture());
        ArgumentCaptor<EmailVerificationToken> saved =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        then(tokenRepository).should().save(saved.capture());

        String afterToken = body.getValue().substring(body.getValue().indexOf("token=") + 6);
        String rawToken = afterToken.split("\\s")[0];
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.hash(rawToken));
        // DB 만 들여다봐서는 링크를 만들 수 없어야 한다
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    @DisplayName("새 링크를 내면 그 회원의 이전 링크는 죽는다")
    void sendingInvalidatesPreviousLink() {
        given(memberService.findActive(MEMBER_ID)).willReturn(member("dj@example.com"));

        emailVerificationService.sendVerificationLink(MEMBER_ID);

        then(tokenRepository).should().deleteByMember_Id(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰은 발급 시각 기준 24시간 뒤에 만료된다")
    void tokenExpiresAfterValidity() {
        given(memberService.findActive(MEMBER_ID)).willReturn(member("dj@example.com"));

        emailVerificationService.sendVerificationLink(MEMBER_ID);

        ArgumentCaptor<EmailVerificationToken> saved =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        then(tokenRepository).should().save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(EmailVerificationToken.VALIDITY));
    }

    @Test
    @DisplayName("주소가 없으면 보낼 곳이 없다")
    void cannotSendWithoutEmail() {
        given(memberService.findActive(MEMBER_ID)).willReturn(member(null));

        assertThatThrownBy(() -> emailVerificationService.sendVerificationLink(MEMBER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이메일");
        then(mailSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 인증된 주소에는 다시 보내지 않는다")
    void cannotSendWhenAlreadyVerified() {
        Member verified = member("dj@example.com");
        verified.markEmailVerified();
        given(memberService.findActive(MEMBER_ID)).willReturn(verified);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationLink(MEMBER_ID))
                .isInstanceOf(IllegalStateException.class);
        then(tokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("방금 보냈으면 다시 보내지 않는다 - 주소를 바꿔 가며 누르면 메일 발송 도구가 된다")
    void refusesResendWithinInterval() {
        Member member = member("dj@example.com");
        given(memberService.findActive(MEMBER_ID)).willReturn(member);
        given(tokenRepository.findByMember_Id(MEMBER_ID)).willReturn(Optional.of(
                new EmailVerificationToken(member, "dj@example.com", "hash", NOW.minusSeconds(30))));

        assertThatThrownBy(() -> emailVerificationService.sendVerificationLink(MEMBER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("방금");
        then(mailSender).shouldHaveNoInteractions();
        // 거절했으면 이전 링크도 살아 있어야 한다
        then(tokenRepository).should(never()).deleteByMember_Id(MEMBER_ID);
    }

    @Test
    @DisplayName("간격이 지났으면 다시 보낸다")
    void allowsResendAfterInterval() {
        Member member = member("dj@example.com");
        given(memberService.findActive(MEMBER_ID)).willReturn(member);
        given(tokenRepository.findByMember_Id(MEMBER_ID)).willReturn(Optional.of(
                new EmailVerificationToken(member, "dj@example.com", "hash", NOW.minusMinutes(5))));

        emailVerificationService.sendVerificationLink(MEMBER_ID);

        then(mailSender).should().send(eq("dj@example.com"), anyString(), anyString());
    }

    // ── 확인 ──────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 링크를 누르면 인증되고 토큰은 폐기된다")
    void verifyMarksVerifiedAndDiscardsToken() {
        Member member = member("dj@example.com");
        EmailVerificationToken token = new EmailVerificationToken(
                member, "dj@example.com", TokenHasher.hash("raw"), NOW);
        given(tokenRepository.findByTokenHash(TokenHasher.hash("raw"))).willReturn(Optional.of(token));

        boolean verified = emailVerificationService.verify("raw");

        assertThat(verified).isTrue();
        assertThat(member.isEmailVerified()).isTrue();
        // 한 번 쓴 링크는 다시 열리지 않는다
        then(tokenRepository).should().delete(token);
    }

    @Test
    @DisplayName("만료된 링크는 통하지 않는다")
    void expiredLinkFails() {
        Member member = member("dj@example.com");
        EmailVerificationToken token = new EmailVerificationToken(
                member, "dj@example.com", TokenHasher.hash("raw"),
                NOW.minus(EmailVerificationToken.VALIDITY).minusMinutes(1));
        given(tokenRepository.findByTokenHash(TokenHasher.hash("raw"))).willReturn(Optional.of(token));

        assertThat(emailVerificationService.verify("raw")).isFalse();
        assertThat(member.isEmailVerified()).isFalse();
        then(tokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("메일을 보낸 뒤 주소를 또 바꿨으면 옛 링크는 통하지 않는다")
    void linkForOldAddressFails() {
        Member member = member("dj@example.com");
        EmailVerificationToken token = new EmailVerificationToken(
                member, "old@example.com", TokenHasher.hash("raw"), NOW);
        given(tokenRepository.findByTokenHash(TokenHasher.hash("raw"))).willReturn(Optional.of(token));

        assertThat(emailVerificationService.verify("raw")).isFalse();
        assertThat(member.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("없는 토큰이면 false")
    void unknownTokenFails() {
        given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThat(emailVerificationService.verify("raw")).isFalse();
    }

    @Test
    @DisplayName("토큰이 비어 있으면 조회조차 하지 않는다")
    void blankTokenShortCircuits() {
        assertThat(emailVerificationService.verify(null)).isFalse();
        assertThat(emailVerificationService.verify("  ")).isFalse();

        then(tokenRepository).shouldHaveNoInteractions();
    }

    // ── 화면이 묻는 것 ────────────────────────────────────────

    @Test
    @DisplayName("인증이 필요한 주소가 있으면 그 주소를 알려 준다")
    void pendingEmailWhenUnverified() {
        given(memberService.findActive(MEMBER_ID)).willReturn(member("dj@example.com"));

        assertThat(emailVerificationService.pendingEmailOf(MEMBER_ID)).contains("dj@example.com");
    }

    @Test
    @DisplayName("이미 인증됐으면 보낼 것이 없다")
    void noPendingEmailWhenVerified() {
        Member verified = member("dj@example.com");
        verified.markEmailVerified();
        given(memberService.findActive(MEMBER_ID)).willReturn(verified);

        assertThat(emailVerificationService.pendingEmailOf(MEMBER_ID)).isEmpty();
    }
}
