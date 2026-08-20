package com.example.board.config;

import com.example.board.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

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

    /**
     * H2 콘솔 전용 체인.
     *
     * <p>콘솔은 DB 를 브라우저에서 그대로 열 수 있는 창구라, 운영에 열려 있으면 그것으로 끝이다.
     * 그래서 "콘솔이 켜져 있을 때만" 이 체인이 만들어지도록 묶었다 -
     * 예외(csrf 무시·프레임 허용·인증 면제)와 콘솔의 수명이 같아져,
     * 콘솔을 끄면 뚫어 둔 구멍도 함께 사라진다. 둘을 따로 관리하면 언젠가 어긋난다.</p>
     *
     * <p>경로를 문자열로 적지 않고 {@link PathRequest#toH2Console()} 를 쓰는 것은
     * 설정에서 콘솔 경로를 바꿔도 예외 범위가 따라오게 하기 위해서다.</p>
     */
    @Bean
    @Order(0)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PathRequest.toH2Console())
                // 콘솔은 자체 폼으로 동작하고 프레임을 쓴다 - 이 체인 안에서만 허용한다
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfTokenHandler()))
                // 프레임 차단은 기본값(DENY)을 그대로 둔다 - 클릭재킹 방어를 h2 때문에 풀어 줄 이유가 없다
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 회원 기능·정적 리소스는 공개 (로그아웃은 쿠키 삭제뿐이라 익명도 무해)
                        // 비밀번호 찾기는 로그인할 수 없는 상태에서 쓰는 기능이라 공개다
                        .requestMatchers("/login", "/logout", "/signup", "/password/**",
                                "/css/**", "/js/**", "/images/**", "/error").permitAll()
                        // 게시판 읽기(목록·상세·첨부파일)는 공개, 쓰기는 인증 필요
                        .requestMatchers(HttpMethod.GET, "/", "/posts", "/posts/{id:\\d+}", "/files/**").permitAll()
                        // 캘린더 구독은 구글 캘린더가 로그인 없이 읽어 가야 한다 (주소의 토큰이 곧 열쇠).
                        // .ics 로 끝나는 경로만 열어, 같은 prefix 의 내려받기·발급은 인증 아래 남긴다
                        .requestMatchers(HttpMethod.GET, "/calendar/*.ics").permitAll()
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

    /**
     * CSRF 토큰을 필터에서 곧바로 확정한다.
     *
     * <p>기본값은 <b>미루기</b>다 - 화면이 토큰을 읽는 순간에야 만들어 쿠키로 내려보낸다.
     * 세션에 담아 두는 구성에서는 그래도 되지만, 여기처럼 쿠키가 유일한 보관소면 조용히 어긋난다.
     * 토큰을 읽지 않고 지나간 응답에는 쿠키가 실리지 않고, 그다음 POST 에서 서버가 새 토큰을 만들어
     * 비교하므로 폼에 이미 박혀 있던 값과 맞을 수가 없다. 실제로 온보딩 '건너뛰고 둘러보기' 가
     * 403 으로 막혔고, 브라우저에는 XSRF-TOKEN 쿠키가 아예 없었다.</p>
     *
     * <p>요청 속성 이름을 비우면 핸들러가 그 자리에서 토큰을 확정한다. 그래서 응답이 무엇을 그리든,
     * 심지어 아무것도 그리지 않아도 쿠키가 먼저 정해진다. BREACH 대비 마스킹(Xor)은 그대로 둔다 -
     * 문제는 마스킹이 아니라 <b>언제</b> 만드느냐였다.</p>
     */
    private static CsrfTokenRequestAttributeHandler eagerCsrfTokenHandler() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
