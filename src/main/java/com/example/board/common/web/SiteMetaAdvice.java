package com.example.board.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면이 공통으로 쓰는 사이트 값.
 *
 * <p>― 왜 전역인가<br>
 * 공유 미리보기(OG)의 이미지 주소는 <b>절대 경로여야</b> 한다. 카톡·슬랙은 우리 서버가 아니라
 * 자기 서버에서 그 주소를 받아 가기 때문이다. 그런데 레이아웃은 스무 개 화면이 함께 쓰므로,
 * 화면마다 이 값을 넣게 하면 언젠가 몇 개가 빠지고 그 글만 미리보기가 깨진다 -
 * 빠진 것을 알아차리는 자리가 <b>남의 메신저</b>라 더 늦게 발견된다.
 *
 * <p>Thymeleaf 3.1 에서 {@code #httpServletRequest} 가 사라져 템플릿이 요청 주소를 직접 볼 수도 없다.
 * 어차피 리버스 프록시 뒤에서는 요청 주소보다 설정값이 정확하다.
 */
@ControllerAdvice
public class SiteMetaAdvice {

    private final String baseUrl;

    public SiteMetaAdvice(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        // 끝의 '/' 유무로 주소가 '//icons/...' 가 되는 것을 여기서 한 번만 정리한다
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return baseUrl;
    }
}
