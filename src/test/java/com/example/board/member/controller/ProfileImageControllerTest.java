package com.example.board.member.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.CookiePolicy;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.auth.service.TokenService;
import com.example.board.config.SecurityConfig;
import com.example.board.file.store.FileStore;
import com.example.board.member.domain.Member;
import com.example.board.member.domain.ProfileImage;
import com.example.board.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 프로필 사진 서빙.
 *
 * <p>공개 경로이고 <b>인라인으로</b> 내려가므로, "이미지라고 주장하는 파일" 을 그대로 내보내면
 * 그 자체가 공격 경로가 된다. 그래서 업로드할 때 파일 내용으로 정해 둔 Content-Type 을 쓰고,
 * 그것이 이미지가 아니면 아예 내주지 않는다.</p>
 */
@WebMvcTest(ProfileImageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        AuthCookies.class, CookiePolicy.class})
class ProfileImageControllerTest {

    @TempDir
    static Path tempDir;

    @Autowired MockMvc mockMvc;
    @MockBean MemberRepository memberRepository;
    @MockBean FileStore fileStore;
    @MockBean TokenService tokenService;

    /** 회원과 사진을 준비한다. 스터빙을 겹쳐 쓰지 않도록 회원을 먼저 만들고 나서 등록한다 */
    private Member memberWithImage(String storedName, String contentType) {
        Member member = new Member("dongju", "encoded", "동주");
        member.changeProfileImage(new ProfileImage(storedName, contentType));
        return member;
    }

    private void given(long memberId, Member member) {
        org.mockito.BDDMockito.given(memberRepository.findById(memberId))
                .willReturn(Optional.of(member));
    }

    /** 디스크에 실제 파일이 있는 상태 */
    private void storedOnDisk(String storedName) throws Exception {
        Files.write(tempDir.resolve(storedName), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        org.mockito.BDDMockito.given(fileStore.getFullPath(storedName))
                .willReturn(tempDir.resolve(storedName).toString());
    }

    @Test
    @DisplayName("사진이 있으면 저장해 둔 Content-Type 으로 내려 준다 - 로그인 없이 열린다")
    void servesImage() throws Exception {
        storedOnDisk("a.png");
        given(1L, memberWithImage("a.png", "image/png"));

        mockMvc.perform(get("/members/1/avatar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    @DisplayName("사진이 바뀌지 않았으면 304 - 주소가 고정이라 시간이 아니라 ETag 로 되묻는다")
    void notModifiedWhenUnchanged() throws Exception {
        storedOnDisk("b.png");
        given(1L, memberWithImage("b.png", "image/png"));

        mockMvc.perform(get("/members/1/avatar").header("If-None-Match", "\"b.png\""))
                .andExpect(status().isNotModified());
    }

    @Test
    @DisplayName("사진을 바꾸면 ETag 가 달라져 새로 받아 간다")
    void refetchesAfterChange() throws Exception {
        storedOnDisk("c.png");
        given(1L, memberWithImage("c.png", "image/png"));

        // 브라우저가 들고 있는 것은 옛 사진의 ETag 다
        mockMvc.perform(get("/members/1/avatar").header("If-None-Match", "\"old.png\""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사진이 없으면 404 - 화면은 글자 아바타를 그린다")
    void noImage() throws Exception {
        given(1L, new Member("dongju", "encoded", "동주"));

        mockMvc.perform(get("/members/1/avatar")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 회원이면 404")
    void unknownMember() throws Exception {
        org.mockito.BDDMockito.given(memberRepository.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/members/99/avatar")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미지가 아닌 Content-Type 은 내주지 않는다 - 인라인으로 나가는 경로다")
    void refusesNonImage() throws Exception {
        given(1L, memberWithImage("d.png", "text/html"));

        mockMvc.perform(get("/members/1/avatar")).andExpect(status().isNotFound());
    }
}
