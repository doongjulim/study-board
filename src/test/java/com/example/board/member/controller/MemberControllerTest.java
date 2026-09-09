package com.example.board.member.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.dto.SignupForm;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class, CookiePolicy.class})
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MemberService memberService;
    @MockBean TokenService tokenService;

    @Test
    @DisplayName("GET /signup - 회원가입 폼이 200 을 반환한다")
    void signupForm() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeExists("signupForm"));
    }

    @Test
    @DisplayName("POST /signup - 유효한 입력이면 가입 후 로그인 페이지로 이동한다")
    void signup_success() throws Exception {
        given(memberService.signup(any(SignupForm.class))).willReturn(1L);

        mockMvc.perform(post("/signup").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("nickname", "테스터"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(memberService).should().signup(any(SignupForm.class));
    }

    @Test
    @DisplayName("POST /signup - 비밀번호 확인이 다르면 폼으로 돌아가고 가입하지 않는다")
    void signup_passwordMismatch() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("passwordConfirm", "different123")
                        .param("nickname", "테스터"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "passwordConfirm"));

        then(memberService).should(never()).signup(any());
    }

    @Test
    @DisplayName("POST /signup - 아이디 형식이 틀리면 폼으로 돌아간다")
    void signup_invalidLoginId() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("loginId", "한글아이디")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("nickname", "테스터"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupForm", "loginId"));

        then(memberService).should(never()).signup(any());
    }

    @Test
    @DisplayName("POST /signup - 중복 아이디면 해당 필드 오류와 함께 폼으로 돌아간다")
    void signup_duplicateLoginId() throws Exception {
        given(memberService.signup(any(SignupForm.class)))
                .willThrow(new DuplicateMemberException("loginId", "이미 사용 중인 아이디입니다."));

        mockMvc.perform(post("/signup").with(csrf())
                        .param("loginId", "tester1")
                        .param("password", "password123")
                        .param("passwordConfirm", "password123")
                        .param("nickname", "테스터"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "loginId"));
    }
}
