package com.example.board.auth.service;

import com.example.board.auth.repository.RefreshTokenRepository;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefreshTokenServiceIntegrationTest {

    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(new Member("tester1", "encoded-password", "테스터"));
    }

    @Test
    @DisplayName("발급한 토큰의 원문은 DB 에 저장되지 않는다 (해시만 저장)")
    void issue_storesHashOnly() {
        String rawToken = refreshTokenService.issue(member);
        em.flush();

        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(stored -> {
                    assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
                    assertThat(stored.getTokenHash()).hasSize(64); // SHA-256 hex
                    assertThat(stored.getMember().getId()).isEqualTo(member.getId());
                });
    }

    @Test
    @DisplayName("유효한 토큰으로 회전하면 새 토큰이 나오고 이전 토큰은 무효가 된다")
    void rotate_invalidatesPreviousToken() {
        String firstToken = refreshTokenService.issue(member);

        Optional<RefreshTokenService.Rotation> rotation = refreshTokenService.rotate(firstToken);

        assertThat(rotation).isPresent();
        assertThat(rotation.get().newRawToken()).isNotEqualTo(firstToken);
        assertThat(rotation.get().member().getId()).isEqualTo(member.getId());
        // 이미 사용한 토큰으로는 다시 회전할 수 없다
        assertThat(refreshTokenService.rotate(firstToken)).isEmpty();
        // 새 토큰은 유효하다
        assertThat(refreshTokenService.rotate(rotation.get().newRawToken())).isPresent();
    }

    @Test
    @DisplayName("폐기(로그아웃)한 토큰으로는 회전할 수 없다")
    void revoke_blocksRotation() {
        String rawToken = refreshTokenService.issue(member);

        refreshTokenService.revoke(rawToken);

        assertThat(refreshTokenService.rotate(rawToken)).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰으로는 회전할 수 없고 해당 토큰은 정리된다")
    void rotate_expiredToken() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDays", -1L); // 이미 만료된 토큰 발급
        String expiredToken = refreshTokenService.issue(member);

        assertThat(refreshTokenService.rotate(expiredToken)).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로는 회전할 수 없다")
    void rotate_unknownToken() {
        assertThat(refreshTokenService.rotate("no-such-token")).isEmpty();
    }

    @Test
    @DisplayName("deleteExpired - 만료된 토큰만 정리한다")
    void deleteExpired() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDays", -1L);
        refreshTokenService.issue(member);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDays", 14L);
        String liveToken = refreshTokenService.issue(member);
        em.flush();

        int deleted = refreshTokenService.deleteExpired(LocalDateTime.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenService.rotate(liveToken)).isPresent();
    }
}
