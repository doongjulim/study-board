package com.example.board.member.controller;

import com.example.board.file.store.FileStore;
import com.example.board.member.domain.Member;
import com.example.board.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 프로필 사진을 내려 준다.
 *
 * <p>공개다 - 닉네임이 이미 게시판·댓글·순위표에 그대로 보이므로, 그 옆에 붙는 얼굴도 같은 범위다.
 * 대신 <b>회원 id 로만</b> 접근하게 해서 저장된 파일 이름이 밖으로 드러나지 않게 한다.</p>
 *
 * <p>Content-Type 은 업로드할 때 파일 내용에서 확인해 저장해 둔 값을 쓴다.
 * 그 값이 이미지가 아니면 내려 주지 않는다 - 인라인으로 나가는 경로라 여기서 한 번 더 막는다.</p>
 */
@Controller
@RequiredArgsConstructor
public class ProfileImageController {

    /** 사진은 바뀌는 일이 드물다. 회원 목록마다 다시 받아 갈 이유가 없다 */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final MemberRepository memberRepository;
    private final FileStore fileStore;

    @GetMapping("/members/{memberId}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long memberId) throws MalformedURLException {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null || !member.hasProfileImage()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = member.getProfileImage().getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(
                Paths.get(fileStore.getFullPath(member.getProfileImage().getStoredName())).toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
