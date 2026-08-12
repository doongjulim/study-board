package com.example.board.member.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.member.dto.PasswordChangeForm;
import com.example.board.member.dto.ProfileForm;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MyPageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class MyPageControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired MockMvc mockMvc;
    @MockBean MemberService memberService;
    @MockBean TokenService tokenService;

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private Member member() {
        Member member = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "createdAt", LocalDateTime.of(2026, 5, 1, 9, 0));
        return member;
    }

    @Nested
    @DisplayName("조회")
    class View {

        @Test
        @DisplayName("GET /me - 내 정보 화면을 보여 준다")
        void myPage() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());

            mockMvc.perform(get("/me").with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("member/my-page"))
                    .andExpect(model().attributeExists("member", "profileForm", "passwordChangeForm"));
        }

        @Test
        @DisplayName("GET /me - 비로그인은 로그인 화면으로 보낸다")
        void requiresLogin() throws Exception {
            mockMvc.perform(get("/me"))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("성공하면 마이페이지로 돌아간다")
        void success() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());

            mockMvc.perform(post("/me/profile")
                            .param("nickname", "새닉")
                            .param("email", "me@example.com")
                            .param("dailyGoalMinutes", "90")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/me"));

            then(memberService).should().updateProfile(eq(MEMBER_ID), any(ProfileForm.class));
        }

        @Test
        @DisplayName("닉네임이 비면 저장하지 않고 화면을 다시 보여 준다")
        void validationError() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());

            mockMvc.perform(post("/me/profile")
                            .param("nickname", "")
                            .param("dailyGoalMinutes", "30")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("member/my-page"));

            then(memberService).should(never()).updateProfile(any(), any());
        }

        @Test
        @DisplayName("중복 닉네임이면 해당 필드에 오류를 표시한다")
        void duplicateNickname() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());
            willThrow(new DuplicateMemberException("nickname", "이미 사용 중인 닉네임입니다."))
                    .given(memberService).updateProfile(eq(MEMBER_ID), any(ProfileForm.class));

            mockMvc.perform(post("/me/profile")
                            .param("nickname", "남의닉")
                            .param("dailyGoalMinutes", "30")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("profileForm", "nickname"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("성공하면 쿠키를 지우고 로그인 화면으로 보낸다")
        void success() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());

            mockMvc.perform(post("/me/password")
                            .param("currentPassword", "password123")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "new-password-1")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"))
                    .andExpect(cookie().maxAge("ACCESS_TOKEN", 0))
                    .andExpect(cookie().maxAge("REFRESH_TOKEN", 0));
        }

        @Test
        @DisplayName("새 비밀번호 확인이 다르면 저장하지 않는다")
        void confirmMismatch() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());

            mockMvc.perform(post("/me/password")
                            .param("currentPassword", "password123")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "different-1")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("passwordChangeForm", "newPasswordConfirm"));

            then(memberService).should(never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 해당 필드에 오류를 표시한다")
        void wrongCurrentPassword() throws Exception {
            given(memberService.findActive(MEMBER_ID)).willReturn(member());
            willThrow(new LoginFailedException())
                    .given(memberService).changePassword(eq(MEMBER_ID), any(PasswordChangeForm.class));

            mockMvc.perform(post("/me/password")
                            .param("currentPassword", "wrong")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "new-password-1")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("passwordChangeForm", "currentPassword"));
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        @Test
        @DisplayName("성공하면 쿠키를 지우고 로그인 화면으로 보낸다")
        void success() throws Exception {
            mockMvc.perform(post("/me/withdraw")
                            .param("password", "password123")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"))
                    .andExpect(cookie().maxAge("ACCESS_TOKEN", 0));

            then(memberService).should().withdraw(MEMBER_ID, "password123");
        }

        @Test
        @DisplayName("비밀번호가 틀리면 마이페이지로 돌려보낸다")
        void wrongPassword() throws Exception {
            willThrow(new LoginFailedException())
                    .given(memberService).withdraw(MEMBER_ID, "wrong");

            mockMvc.perform(post("/me/withdraw")
                            .param("password", "wrong")
                            .with(csrf()).with(memberAuth()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/me"));
        }

        @Test
        @DisplayName("CSRF 토큰이 없으면 거부한다")
        void requiresCsrf() throws Exception {
            mockMvc.perform(post("/me/withdraw").param("password", "password123").with(memberAuth()))
                    .andExpect(status().isForbidden());

            then(memberService).should(never()).withdraw(any(), any());
        }
    }
}
