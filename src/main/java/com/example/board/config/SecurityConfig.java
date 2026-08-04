package com.example.board.config;

import com.example.board.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * JWT(HttpOnly 쿠키) 기반 무상태 보안 설정.
 * 세션을 만들지 않으므로 CSRF 토큰도 쿠키 저장소를 사용한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // h2-console
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 회원 기능·정적 리소스는 공개 (로그아웃은 쿠키 삭제뿐이라 익명도 무해)
                        .requestMatchers("/login", "/logout", "/signup",
                                "/css/**", "/js/**", "/h2-console/**", "/error").permitAll()
                        // 게시판 읽기(목록·상세·첨부파일)는 공개, 쓰기는 인증 필요
                        .requestMatchers(HttpMethod.GET, "/", "/posts", "/posts/{id:\\d+}", "/files/**").permitAll()
                        // 플래너·알림 등 나머지는 전부 로그인 필요
                        .anyRequest().authenticated())
                // 미인증 접근은 원래 가려던 경로를 들고 로그인 페이지로 보낸다
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, ex) -> {
                    String target = request.getRequestURI()
                            + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
                    response.sendRedirect("/login?redirect="
                            + URLEncoder.encode(target, StandardCharsets.UTF_8));
                }))
                .formLogin(AbstractHttpConfigurer::disable)   // 세션 기반이라 사용하지 않음 - AuthController 가 담당
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)      // 쿠키 삭제 방식 로그아웃도 AuthController 가 담당
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
