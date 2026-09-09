package com.example.board.member.service;

import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * 소셜 계정 생성을 <b>유니크 제약이 살아 있는 DB 위에서</b> 확인한다.
 *
 * <p>서비스 단위 테스트는 리포지토리가 mock 이라 제약을 모른다. 그래서 제공자가 준 이메일을
 * 그대로 저장하는 코드가 통과했고, 같은 주소로 이미 가입한 사람이 소셜 로그인을 시도하는 순간
 * 저장이 깨졌다 - 그것도 성공 핸들러 안에서 나는 예외라 오류 화면이었다.</p>
 */
@SpringBootTest
@Transactional
class OAuthMemberCreationIntegrationTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("이미 가입된 이메일과 같은 주소의 소셜 계정도 만들어진다 - 이메일만 비운다")
    void createsEvenWhenEmailAlreadyTaken() {
        memberRepository.save(new Member("dongju", "encoded-password", "동주", "dj@example.com"));
        em.flush();

        Member created = memberService.findOrCreateOAuthMember(
                "google", "1234", "dj@example.com", "동주");
        em.flush(); // 유니크 제약은 여기서 걸린다

        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isNull();
        assertThat(created.getLoginId()).isEqualTo("google_1234");
        // 이어 붙이지 않는다 - 같은 이메일이라고 남의 계정을 넘겨주면 그게 계정 탈취다
        assertThat(created.getId()).isNotEqualTo(
                memberRepository.findByLoginId("dongju").orElseThrow().getId());
    }

    @Test
    @DisplayName("닉네임까지 겹쳐도 만들어진다 - 둘 다 비켜 가는 방식이 있다")
    void createsWhenNicknameAlsoTaken() {
        memberRepository.save(new Member("dongju", "encoded-password", "동주", "dj@example.com"));
        em.flush();

        Member created = memberService.findOrCreateOAuthMember(
                "kakao", "9999", "dj@example.com", "동주");
        em.flush();

        assertThat(created.getNickname()).isEqualTo("동주2");
        assertThat(created.getEmail()).isNull();
    }
}
