package com.example.board.member.service;

import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.service.RefreshTokenService;
import com.example.board.file.store.FileStore;
import com.example.board.file.store.TransactionalFileRemover;
import com.example.board.member.domain.Member;
import com.example.board.post.domain.AttachedFile;
import com.example.board.member.domain.ProfileImage;
import com.example.board.member.dto.PasswordChangeForm;
import com.example.board.member.dto.ProfileForm;
import com.example.board.member.dto.SignupForm;
import com.example.board.member.event.MemberWithdrawnEvent;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);

    @Mock MemberRepository memberRepository;
    @Mock RefreshTokenService refreshTokenService;
    @Mock ApplicationEventPublisher eventPublisher;
    /** 프로필 사진 저장·삭제 */
    @Mock FileStore fileStore;
    @Mock TransactionalFileRemover fileRemover;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        memberService = new MemberService(memberRepository, passwordEncoder,
                refreshTokenService, eventPublisher, fileStore, fileRemover, fixed);
    }

    private SignupForm signupForm() {
        SignupForm form = new SignupForm();
        form.setLoginId("tester1");
        form.setPassword("password123");
        form.setPasswordConfirm("password123");
        form.setNickname("테스터");
        return form;
    }

    private Member member() {
        Member member = new Member("tester1", passwordEncoder.encode("password123"), "테스터");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("비밀번호는 BCrypt 로 해시되어 저장된다")
        void encodesPassword() {
            given(memberRepository.existsByLoginId("tester1")).willReturn(false);
            given(memberRepository.existsByNickname("테스터")).willReturn(false);
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

            memberService.signup(signupForm());

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            then(memberRepository).should().save(captor.capture());
            Member saved = captor.getValue();
            assertThat(saved.getPassword()).isNotEqualTo("password123");
            assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("이미 사용 중인 아이디면 DuplicateMemberException(loginId) 이 발생한다")
        void duplicateLoginId() {
            given(memberRepository.existsByLoginId("tester1")).willReturn(true);

            assertThatThrownBy(() -> memberService.signup(signupForm()))
                    .isInstanceOf(DuplicateMemberException.class)
                    .extracting("field").isEqualTo("loginId");
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임이면 DuplicateMemberException(nickname) 이 발생한다")
        void duplicateNickname() {
            given(memberRepository.existsByLoginId("tester1")).willReturn(false);
            given(memberRepository.existsByNickname("테스터")).willReturn(true);

            assertThatThrownBy(() -> memberService.signup(signupForm()))
                    .isInstanceOf(DuplicateMemberException.class)
                    .extracting("field").isEqualTo("nickname");
        }

        @Test
        @DisplayName("이메일을 넣지 않으면 중복 검사를 하지 않는다 (선택 입력)")
        void emailIsOptional() {
            given(memberRepository.existsByLoginId("tester1")).willReturn(false);
            given(memberRepository.existsByNickname("테스터")).willReturn(false);
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

            memberService.signup(signupForm());

            then(memberRepository).should(never()).existsByEmail(any());
        }

        @Test
        @DisplayName("이미 사용 중인 이메일이면 DuplicateMemberException(email) 이 발생한다")
        void duplicateEmail() {
            SignupForm form = signupForm();
            form.setEmail("me@example.com");
            given(memberRepository.existsByLoginId("tester1")).willReturn(false);
            given(memberRepository.existsByNickname("테스터")).willReturn(false);
            given(memberRepository.existsByEmail("me@example.com")).willReturn(true);

            assertThatThrownBy(() -> memberService.signup(form))
                    .isInstanceOf(DuplicateMemberException.class)
                    .extracting("field").isEqualTo("email");
        }
    }

    @Nested
    @DisplayName("로그인")
    class Authenticate {

        @Test
        @DisplayName("아이디와 비밀번호가 일치하면 회원을 반환한다")
        void success() {
            Member member = member();
            given(memberRepository.findByLoginId("tester1")).willReturn(Optional.of(member));

            assertThat(memberService.authenticate("tester1", "password123")).isSameAs(member);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 LoginFailedException 이 발생한다")
        void wrongPassword() {
            given(memberRepository.findByLoginId("tester1")).willReturn(Optional.of(member()));

            assertThatThrownBy(() -> memberService.authenticate("tester1", "wrong-password"))
                    .isInstanceOf(LoginFailedException.class);
        }

        @Test
        @DisplayName("존재하지 않는 아이디면 LoginFailedException 이 발생한다")
        void unknownLoginId() {
            given(memberRepository.findByLoginId("nobody")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.authenticate("nobody", "password123"))
                    .isInstanceOf(LoginFailedException.class);
        }

        @Test
        @DisplayName("탈퇴한 회원은 로그인할 수 없다")
        void withdrawnCannotLogin() {
            Member member = member();
            member.withdraw(NOW);
            given(memberRepository.findByLoginId("tester1")).willReturn(Optional.of(member));

            assertThatThrownBy(() -> memberService.authenticate("tester1", "password123"))
                    .isInstanceOf(LoginFailedException.class);
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        private ProfileForm form(String nickname, String email, int goal) {
            ProfileForm form = new ProfileForm();
            form.setNickname(nickname);
            form.setEmail(email);
            form.setDailyGoalMinutes(goal);
            return form;
        }

        @Test
        @DisplayName("닉네임·이메일·목표 시간을 한 번에 바꾼다")
        void updatesAll() {
            Member member = member();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
            given(memberRepository.existsByNickname("새닉")).willReturn(false);
            given(memberRepository.existsByEmail("me@example.com")).willReturn(false);

            memberService.updateProfile(MEMBER_ID, form("새닉", "me@example.com", 90));

            assertThat(member.getNickname()).isEqualTo("새닉");
            assertThat(member.getEmail()).isEqualTo("me@example.com");
            assertThat(member.getDailyGoalMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("닉네임을 그대로 두면 중복 검사를 하지 않는다 (본인 닉네임에 걸리지 않게)")
        void skipsCheckWhenNicknameUnchanged() {
            Member member = member();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.updateProfile(MEMBER_ID, form("테스터", null, 60));

            then(memberRepository).should(never()).existsByNickname(any());
            assertThat(member.getDailyGoalMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("남이 쓰는 닉네임으로는 바꿀 수 없다")
        void rejectsTakenNickname() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));
            given(memberRepository.existsByNickname("남의닉")).willReturn(true);

            assertThatThrownBy(() -> memberService.updateProfile(MEMBER_ID, form("남의닉", null, 60)))
                    .isInstanceOf(DuplicateMemberException.class)
                    .extracting("field").isEqualTo("nickname");
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        private PasswordChangeForm form(String current, String next) {
            PasswordChangeForm form = new PasswordChangeForm();
            form.setCurrentPassword(current);
            form.setNewPassword(next);
            form.setNewPasswordConfirm(next);
            return form;
        }

        @Test
        @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 교체한다")
        void changes() {
            Member member = member();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.changePassword(MEMBER_ID, form("password123", "new-password-1"));

            assertThat(passwordEncoder.matches("new-password-1", member.getPassword())).isTrue();
        }

        @Test
        @DisplayName("비밀번호를 바꾸면 모든 기기에서 로그아웃된다")
        void revokesEveryDevice() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));

            memberService.changePassword(MEMBER_ID, form("password123", "new-password-1"));

            then(refreshTokenService).should().revokeAll(MEMBER_ID);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 바뀌지 않는다")
        void rejectsWrongCurrentPassword() {
            Member member = member();
            String before = member.getPassword();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            assertThatThrownBy(() -> memberService.changePassword(MEMBER_ID, form("wrong", "new-password-1")))
                    .isInstanceOf(LoginFailedException.class);

            assertThat(member.getPassword()).isEqualTo(before);
            then(refreshTokenService).should(never()).revokeAll(any());
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("비밀번호를 확인한 뒤 익명화한다")
        void anonymizes() {
            Member member = member();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            memberService.withdraw(MEMBER_ID, "password123");

            assertThat(member.isWithdrawn()).isTrue();
            assertThat(member.getNickname()).isEqualTo("탈퇴한 회원7");
        }

        @Test
        @DisplayName("각 모듈이 개인 데이터를 정리하도록 이벤트를 발행한다")
        void publishesEvent() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));

            memberService.withdraw(MEMBER_ID, "password123");

            then(eventPublisher).should().publishEvent(new MemberWithdrawnEvent(MEMBER_ID));
        }

        @Test
        @DisplayName("탈퇴하면 모든 기기에서 로그아웃된다")
        void revokesEveryDevice() {
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member()));

            memberService.withdraw(MEMBER_ID, "password123");

            then(refreshTokenService).should().revokeAll(MEMBER_ID);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 탈퇴되지 않고 데이터도 지워지지 않는다")
        void rejectsWrongPassword() {
            Member member = member();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

            assertThatThrownBy(() -> memberService.withdraw(MEMBER_ID, "wrong-password"))
                    .isInstanceOf(LoginFailedException.class);

            assertThat(member.isWithdrawn()).isFalse();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    // ── 프로필 사진 ───────────────────────────────────────────

    @Test
    @DisplayName("사진을 바꾸면 이전 사진은 커밋된 뒤에 지운다")
    void changeProfileImage_removesPreviousAfterCommit() throws Exception {
        // 지금 지우면 트랜잭션이 롤백됐을 때 회원 행은 남는데 파일만 사라진다
        Member member = member();
        member.changeProfileImage(new ProfileImage("old.png", "image/png"));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(fileStore.storeImage(any())).willReturn(
                new AttachedFile("new.png", "new-stored.png", "image/png", 100));

        memberService.changeProfileImage(MEMBER_ID, imageOf(1024));

        assertThat(member.getProfileImage().getStoredName()).isEqualTo("new-stored.png");
        then(fileRemover).should().removeAfterCommit("old.png");
    }

    @Test
    @DisplayName("2MB 를 넘는 사진은 저장하기 전에 거절한다")
    void changeProfileImage_rejectsOversized() {
        // 아바타는 화면에서 40px 로 그려진다. 받아 두면 디스크와 대역폭만 쓴다
        assertThatThrownBy(() -> memberService.changeProfileImage(MEMBER_ID, imageOf(3 * 1024 * 1024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2MB");
        then(fileStore).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("빈 파일은 사진이 아니다")
    void changeProfileImage_rejectsEmpty() {
        assertThatThrownBy(() -> memberService.changeProfileImage(MEMBER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> memberService.changeProfileImage(
                MEMBER_ID, new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        then(fileStore).shouldHaveNoInteractions();
    }

    private MockMultipartFile imageOf(int bytes) {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[bytes]);
    }

    // ── 소셜 로그인 ───────────────────────────────────────────

    @Test
    @DisplayName("처음 온 소셜 계정이면 만들고, 제공자 id 로 다시 찾는다")
    void findOrCreateOAuthMember_createsOnce() {
        given(memberRepository.findByOauthProviderAndOauthProviderId("google", "1234"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동주")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주");

        assertThat(created.isSocialAccount()).isTrue();
        assertThat(created.getLoginId()).isEqualTo("google_1234");
        assertThat(created.getNickname()).isEqualTo("동주");
    }

    @Test
    @DisplayName("이미 있는 소셜 계정이면 새로 만들지 않는다")
    void findOrCreateOAuthMember_reusesExisting() {
        Member existing = Member.ofOAuth("google_1234", "동주", "dj@example.com", "google", "1234");
        given(memberRepository.findByOauthProviderAndOauthProviderId("google", "1234"))
                .willReturn(Optional.of(existing));

        Member found = memberService.findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주");

        assertThat(found).isSameAs(existing);
        then(memberRepository).should(never()).save(any(Member.class));
    }

    @Test
    @DisplayName("같은 id 라도 제공자가 다르면 다른 사람이다")
    void findOrCreateOAuthMember_providerIsPartOfIdentity() {
        given(memberRepository.findByOauthProviderAndOauthProviderId("kakao", "1234"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동주")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("kakao", "1234", null, "동주");

        assertThat(created.getLoginId()).isEqualTo("kakao_1234");
    }

    @Test
    @DisplayName("닉네임이 이미 쓰이면 숫자를 붙여 비켜 간다 - 소셜 로그인은 그 자리에서 고칠 기회가 없다")
    void findOrCreateOAuthMember_avoidsNicknameClash() {
        given(memberRepository.findByOauthProviderAndOauthProviderId("google", "1234"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동주")).willReturn(true);
        given(memberRepository.existsByNickname("동주2")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("google", "1234", null, "동주");

        assertThat(created.getNickname()).isEqualTo("동주2");
    }

    @Test
    @DisplayName("이메일을 주지 않는 제공자도 있다 - 그래도 계정은 만들어진다")
    void findOrCreateOAuthMember_withoutEmail() {
        given(memberRepository.findByOauthProviderAndOauthProviderId("kakao", "9"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("회원")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("kakao", "9", null, null);

        assertThat(created.getEmail()).isNull();
        // 이메일이 없으면 인증이라는 개념 자체가 없다
        assertThat(created.needsEmailVerification()).isFalse();
    }

    @Test
    @DisplayName("이미 다른 회원이 쓰는 이메일이면 비워 둔 채로 계정을 만든다")
    void findOrCreateOAuthMember_skipsTakenEmail() {
        // email 에는 유니크 제약이 있다. 그대로 넣으면 성공 핸들러 안에서 저장이 깨져
        // 로그인 실패 화면도 아닌 오류 화면이 되고, 그 사람은 소셜 로그인을 영영 쓸 수 없다.
        // 그렇다고 같은 주소라고 기존 계정에 이어 붙이면 남의 계정을 가져가는 길이 열린다
        given(memberRepository.findByOauthProviderAndOauthProviderId("google", "1234"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동주")).willReturn(false);
        given(memberRepository.existsByEmail("dj@example.com")).willReturn(true);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주");

        assertThat(created.getEmail()).isNull();
        assertThat(created.getLoginId()).isEqualTo("google_1234");
    }

    @Test
    @DisplayName("아무도 안 쓰는 이메일은 그대로 저장한다")
    void findOrCreateOAuthMember_keepsFreeEmail() {
        given(memberRepository.findByOauthProviderAndOauthProviderId("google", "1234"))
                .willReturn(Optional.empty());
        given(memberRepository.existsByNickname("동주")).willReturn(false);
        given(memberRepository.existsByEmail("dj@example.com")).willReturn(false);
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        Member created = memberService.findOrCreateOAuthMember("google", "1234", "dj@example.com", "동주");

        assertThat(created.getEmail()).isEqualTo("dj@example.com");
    }

    // ── 소셜 계정의 비밀번호 경로 ─────────────────────────────

    @Test
    @DisplayName("소셜 계정은 비밀번호를 바꿀 수 없다 - 애초에 비밀번호가 없다")
    void changePassword_rejectedForSocialAccount() {
        // 화면에서 폼을 감추지만, 화면이 유일한 방어선이면 그건 방어가 아니다
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(socialMember()));
        PasswordChangeForm form = new PasswordChangeForm();
        form.setCurrentPassword("무엇이든");
        form.setNewPassword("newpassword123");

        assertThatThrownBy(() -> memberService.changePassword(MEMBER_ID, form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("소셜");
        then(refreshTokenService).should(never()).revokeAll(any());
    }

    @Test
    @DisplayName("소셜 계정은 닉네임을 입력해 탈퇴한다 - 비밀번호로 물으면 탈퇴할 방법이 없다")
    void withdraw_socialAccountConfirmsWithNickname() {
        Member member = socialMember();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        memberService.withdraw(MEMBER_ID, "동주");

        assertThat(member.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("닉네임이 다르면 소셜 계정도 탈퇴되지 않는다")
    void withdraw_socialAccountWrongNickname() {
        Member member = socialMember();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.withdraw(MEMBER_ID, "다른이름"))
                .isInstanceOf(LoginFailedException.class);
        assertThat(member.isWithdrawn()).isFalse();
        then(eventPublisher).should(never()).publishEvent(any(MemberWithdrawnEvent.class));
    }

    private Member socialMember() {
        Member member = Member.ofOAuth("google_1234", "동주", null, "google", "1234");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    @Test
    @DisplayName("제공자가 확인해 준 이메일은 인증된 것으로 본다 - 로그인 자체가 소유 증명이다")
    void oauthEmailIsVerified() {
        Member created = Member.ofOAuth("google_1", "동주", "dj@example.com", "google", "1");

        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.needsEmailVerification()).isFalse();
    }

    @Test
    @DisplayName("소셜 계정의 비밀번호는 어떤 입력과도 맞지 않는다")
    void socialPasswordIsUnusable() {
        Member created = Member.ofOAuth("google_1", "동주", "dj@example.com", "google", "1");

        assertThat(passwordEncoder.matches("", created.getPassword())).isFalse();
        assertThat(passwordEncoder.matches("!social", created.getPassword())).isFalse();
    }

}
