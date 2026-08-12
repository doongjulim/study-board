package com.example.board.auth.service;

import com.example.board.auth.TokenHasher;
import com.example.board.auth.domain.PasswordResetToken;
import com.example.board.auth.repository.PasswordResetTokenRepository;
import com.example.board.mail.MailSender;
import com.example.board.member.domain.Member;
import com.example.board.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);
    private static final String EMAIL = "me@example.com";
    private static final String RAW_TOKEN = "raw-reset-token";

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock MemberService memberService;
    @Mock MailSender mailSender;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        passwordResetService = new PasswordResetService(tokenRepository, memberService, mailSender, fixed);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "http://localhost:8080");
    }

    private Member member() {
        Member member = new Member("tester1", "encoded-password", "테스터", EMAIL);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private PasswordResetToken token(LocalDateTime issuedAt) {
        return new PasswordResetToken(member(), TokenHasher.hash(RAW_TOKEN), issuedAt);
    }

    @Nested
    @DisplayName("재설정 링크 요청")
    class SendResetLink {

        @Test
        @DisplayName("가입된 이메일이면 토큰을 저장하고 링크를 보낸다")
        void sendsLink() {
            given(memberService.findActiveByEmail(EMAIL)).willReturn(Optional.of(member()));

            passwordResetService.sendResetLink(EMAIL);

            then(tokenRepository).should().save(any(PasswordResetToken.class));
            then(mailSender).should().send(eq(EMAIL), anyString(), contains("/password/reset?token="));
        }

        @Test
        @DisplayName("메일에는 원문 토큰이 실리고 DB 에는 해시만 저장된다")
        void storesHashOnly() {
            given(memberService.findActiveByEmail(EMAIL)).willReturn(Optional.of(member()));

            passwordResetService.sendResetLink(EMAIL);

            ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
            then(tokenRepository).should().save(saved.capture());
            ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
            then(mailSender).should().send(anyString(), anyString(), body.capture());

            assertThat(body.getValue()).doesNotContain(saved.getValue().getTokenHash());
        }

        @Test
        @DisplayName("새 링크를 내면 그 회원의 이전 링크는 무효가 된다")
        void invalidatesPreviousLinks() {
            given(memberService.findActiveByEmail(EMAIL)).willReturn(Optional.of(member()));

            passwordResetService.sendResetLink(EMAIL);

            then(tokenRepository).should().deleteByMember_Id(MEMBER_ID);
        }

        @Test
        @DisplayName("가입되지 않은 이메일이면 조용히 끝낸다 (가입 여부를 알려 주지 않는다)")
        void unknownEmailIsSilent() {
            given(memberService.findActiveByEmail("nobody@example.com")).willReturn(Optional.empty());

            passwordResetService.sendResetLink("nobody@example.com");

            then(tokenRepository).shouldHaveNoInteractions();
            then(mailSender).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("링크 유효성")
    class Usable {

        @Test
        @DisplayName("유효 시간 안의 토큰이면 사용할 수 있다")
        void validToken() {
            given(tokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                    .willReturn(Optional.of(token(NOW.minusMinutes(5))));

            assertThat(passwordResetService.isUsable(RAW_TOKEN)).isTrue();
        }

        @Test
        @DisplayName("만료된 토큰은 사용할 수 없다")
        void expiredToken() {
            given(tokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                    .willReturn(Optional.of(token(NOW.minusHours(2))));

            assertThat(passwordResetService.isUsable(RAW_TOKEN)).isFalse();
        }

        @Test
        @DisplayName("없는 토큰은 사용할 수 없다")
        void unknownToken() {
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

            assertThat(passwordResetService.isUsable("made-up")).isFalse();
        }

        @Test
        @DisplayName("토큰이 비어 있으면 조회조차 하지 않는다")
        void blankToken() {
            assertThat(passwordResetService.isUsable("  ")).isFalse();

            then(tokenRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class Reset {

        @Test
        @DisplayName("유효한 토큰이면 비밀번호를 바꾸고 링크를 폐기한다")
        void resets() {
            PasswordResetToken token = token(NOW.minusMinutes(5));
            given(tokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                    .willReturn(Optional.of(token));

            boolean result = passwordResetService.reset(RAW_TOKEN, "new-password-1");

            assertThat(result).isTrue();
            then(memberService).should().resetPassword(MEMBER_ID, "new-password-1");
            then(tokenRepository).should().delete(token);
        }

        @Test
        @DisplayName("만료된 링크로는 바꿀 수 없다")
        void rejectsExpired() {
            given(tokenRepository.findByTokenHash(TokenHasher.hash(RAW_TOKEN)))
                    .willReturn(Optional.of(token(NOW.minusHours(2))));

            assertThat(passwordResetService.reset(RAW_TOKEN, "new-password-1")).isFalse();
            then(memberService).should(never()).resetPassword(any(), anyString());
        }

        @Test
        @DisplayName("이미 쓴 링크는 다시 열리지 않는다 (토큰이 지워져 있으므로)")
        void rejectsUsedToken() {
            given(tokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

            assertThat(passwordResetService.reset(RAW_TOKEN, "new-password-1")).isFalse();
        }
    }
}
