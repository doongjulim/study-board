package com.example.board.member.service;

import com.example.board.auth.exception.LoginFailedException;
import com.example.board.member.domain.Member;
import com.example.board.member.dto.SignupForm;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    private SignupForm signupForm() {
        SignupForm form = new SignupForm();
        form.setLoginId("tester1");
        form.setPassword("password123");
        form.setPasswordConfirm("password123");
        form.setNickname("테스터");
        return form;
    }

    @Test
    @DisplayName("회원가입 시 비밀번호는 BCrypt 로 해시되어 저장된다")
    void signup_encodesPassword() {
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
    void signup_duplicateLoginId() {
        given(memberRepository.existsByLoginId("tester1")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(signupForm()))
                .isInstanceOf(DuplicateMemberException.class)
                .extracting("field").isEqualTo("loginId");
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 DuplicateMemberException(nickname) 이 발생한다")
    void signup_duplicateNickname() {
        given(memberRepository.existsByLoginId("tester1")).willReturn(false);
        given(memberRepository.existsByNickname("테스터")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(signupForm()))
                .isInstanceOf(DuplicateMemberException.class)
                .extracting("field").isEqualTo("nickname");
    }

    @Test
    @DisplayName("아이디와 비밀번호가 일치하면 회원을 반환한다")
    void authenticate_success() {
        Member member = new Member("tester1", passwordEncoder.encode("password123"), "테스터");
        given(memberRepository.findByLoginId("tester1")).willReturn(Optional.of(member));

        assertThat(memberService.authenticate("tester1", "password123")).isSameAs(member);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LoginFailedException 이 발생한다")
    void authenticate_wrongPassword() {
        Member member = new Member("tester1", passwordEncoder.encode("password123"), "테스터");
        given(memberRepository.findByLoginId("tester1")).willReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.authenticate("tester1", "wrong-password"))
                .isInstanceOf(LoginFailedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 아이디면 LoginFailedException 이 발생한다")
    void authenticate_unknownLoginId() {
        given(memberRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.authenticate("nobody", "password123"))
                .isInstanceOf(LoginFailedException.class);
    }
}
