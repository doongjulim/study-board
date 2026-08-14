package com.example.board.auth.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.PasswordResetService;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordResetController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class PasswordResetControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PasswordResetService passwordResetService;
    @MockBean TokenService tokenService;

    /** 호출 여부를 확인하는 테스트가 있으므로 실행 순서와 무관하게 깨끗한 상태에서 시작한다 */
    @BeforeEach
    void resetMocks() {
        Mockito.reset(passwordResetService);
    }

    @Nested
    @DisplayName("링크 요청")
    class Forgot {

        @Test
        @DisplayName("GET /password/forgot - 로그인하지 않아도 볼 수 있다")
        void formIsPublic() throws Exception {
            mockMvc.perform(get("/password/forgot"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/forgot-password"));
        }

        @Test
        @DisplayName("POST /password/forgot - 가입된 주소든 아니든 같은 안내로 끝낸다")
        void alwaysSameResponse() throws Exception {
            mockMvc.perform(post("/password/forgot").param("email", "nobody@example.com").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"))
                    .andExpect(flash().attributeExists("message"));

            then(passwordResetService).should().sendResetLink("nobody@example.com");
        }

        @Test
        @DisplayName("POST /password/forgot - CSRF 토큰이 없으면 거부한다")
        void requiresCsrf() throws Exception {
            mockMvc.perform(post("/password/forgot").param("email", "me@example.com"))
                    .andExpect(status().isForbidden());

            then(passwordResetService).should(never()).sendResetLink(anyString());
        }
    }

    @Nested
    @DisplayName("새 비밀번호 설정")
    class Reset {

        @Test
        @DisplayName("GET /password/reset - 유효한 링크면 입력 폼을 보여 준다")
        void showsFormForValidToken() throws Exception {
            given(passwordResetService.isUsable("good-token")).willReturn(true);

            mockMvc.perform(get("/password/reset").param("token", "good-token"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/reset-password"))
                    .andExpect(model().attributeExists("passwordResetForm"));
        }

        @Test
        @DisplayName("GET /password/reset - 만료된 링크면 다시 요청하도록 안내한다")
        void expiredTokenGoesBack() throws Exception {
            given(passwordResetService.isUsable("stale-token")).willReturn(false);

            mockMvc.perform(get("/password/reset").param("token", "stale-token"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/forgot-password"))
                    .andExpect(model().attributeExists("message"));
        }

        @Test
        @DisplayName("POST /password/reset - 성공하면 로그인 화면으로 보낸다")
        void success() throws Exception {
            given(passwordResetService.reset("good-token", "new-password-1")).willReturn(true);

            mockMvc.perform(post("/password/reset")
                            .param("token", "good-token")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "new-password-1")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        @DisplayName("POST /password/reset - 확인이 다르면 저장하지 않는다")
        void confirmMismatch() throws Exception {
            mockMvc.perform(post("/password/reset")
                            .param("token", "good-token")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "different-1")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("passwordResetForm", "newPasswordConfirm"));

            then(passwordResetService).should(never()).reset(anyString(), anyString());
        }

        @Test
        @DisplayName("POST /password/reset - 제출 사이에 링크가 만료됐으면 다시 요청하도록 안내한다")
        void tokenExpiredBetweenSteps() throws Exception {
            given(passwordResetService.reset(anyString(), anyString())).willReturn(false);

            mockMvc.perform(post("/password/reset")
                            .param("token", "stale-token")
                            .param("newPassword", "new-password-1")
                            .param("newPasswordConfirm", "new-password-1")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/forgot-password"));
        }

        @Test
        @DisplayName("POST /password/reset - 너무 짧은 비밀번호는 거부한다")
        void tooShort() throws Exception {
            mockMvc.perform(post("/password/reset")
                            .param("token", "good-token")
                            .param("newPassword", "short")
                            .param("newPasswordConfirm", "short")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("passwordResetForm", "newPassword"));
        }
    }
}
