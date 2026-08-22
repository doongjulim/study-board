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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * JWT(HttpOnly 쿠키) 기반 보안 설정.
 *
 * <p>인증은 무상태다 - 로그인 상태를 세션이 아니라 토큰이 들고 다닌다. 그래서 서버를 여러 대로
 * 늘려도 세션을 공유할 필요가 없다. CSRF 토큰만은 세션에 둔다(기본값): 쿠키에 두면 한 요청 안에서
 * 토큰이 두 번 만들어질 때 조용히 어긋나기 때문이다 (아래 {@link #filterChain} 의 주석 참고).</p>
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
                // CSRF 토큰은 기본값인 세션 저장소에 둔다 (csrf 설정을 건드리지 않는 것이 그 뜻이다).
                //
                // 쿠키 저장소로 두었다가 온보딩 '건너뛰고 둘러보기' 가 403 으로 막혔다. 한 요청을 처리하는
                // 동안 토큰이 두 번 만들어지면 - 실제로 응답 하나에 Set-Cookie: XSRF-TOKEN 이 두 번
                // 실렸다 - 쿠키 저장소는 두 번째 조회 때도 '요청에 실려 온 쿠키' 만 보므로 첫 번째로 만든
                // 것을 알지 못하고 다른 토큰을 또 만든다. 화면에 박힌 것과 쿠키에 남는 것이 갈린다.
                // 세션 저장소는 같은 요청 안에서 방금 담아 둔 토큰을 그대로 돌려주므로 이 틈이 없다.
                //
                // 서버가 그리는 폼이 주 사용처이고, JS 는 헤더 프래그먼트의 data-csrf-* 에서 이름과 값을
                // 함께 읽으므로 저장소가 무엇이든 따라온다 - 쿠키로 둘 이유가 애초에 없었다.
                //
                // 프레임 차단은 기본값(DENY)을 그대로 둔다 - 클릭재킹 방어를 h2 때문에 풀어 줄 이유가 없다.
                //
                // 세션 관리 기능은 통째로 끈다. 무상태라서 필요 없다는 소극적인 이유가 아니라,
                // 켜 두면 CSRF 가 망가지기 때문이다.
                //
                // SessionManagementFilter 는 "저장소에서 SecurityContext 를 읽어 오지 못했는데 인증은 있다" 를
                // '방금 로그인했다' 로 해석한다. 세션 기반이라면 맞는 추론이다. 그런데 우리는 요청마다
                // JwtAuthenticationFilter 가 토큰을 읽어 인증을 새로 채우고, 저장소는 무상태라 늘 비어 있다.
                // 그래서 이 조건이 로그인한 사람의 <b>모든</b> 요청에서 참이 되고, 그때마다 세션 고정 공격을
                // 막으려고 붙어 있는 CsrfAuthenticationStrategy 가 CSRF 토큰을 갈아 끼운다.
                //
                // 결과는 조용했다 - 화면에 박아 준 토큰이, 그 화면이 부르는 js/css 요청들 때문에
                // 사용자가 버튼을 누르기도 전에 죽어 있었다. 온보딩 '건너뛰고 둘러보기' 가 403 이던 이유다.
                // (로그: CsrfAuthenticationStrategy "Replaced CSRF Token" → CsrfFilter "Invalid CSRF token found")
                //
                // 이 설정을 지우면 SessionManagementFilter 가 사라지고, CsrfConfigurer 도 붙일 곳이 없어
                // 그 전략을 등록하지 않는다. 인증은 그대로 무상태다 - 로그인 상태는 여전히 토큰이 들고 다닌다.
                .sessionManagement(AbstractHttpConfigurer::disable)
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
