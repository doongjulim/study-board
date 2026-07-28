package com.example.board.file.controller;

import com.example.board.post.domain.AttachedFile;
import com.example.board.file.store.FileStore;
import com.example.board.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriUtils;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

@Controller
@RequiredArgsConstructor
public class FileController {

    private final PostService postService;
    private final FileStore fileStore;

    /** 이미지 인라인 표시용 (본문 <img> 태그에서 사용) */
    @GetMapping("/files/{fileId}/view")
    public ResponseEntity<Resource> viewImage(@PathVariable Long fileId) throws MalformedURLException {
        AttachedFile file = postService.findFile(fileId);
        Resource resource = new UrlResource(Paths.get(fileStore.getFullPath(file.getStoredName())).toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        file.getContentType() != null ? file.getContentType() : "application/octet-stream"))
                .body(resource);
    }

    /** 파일 다운로드 (원본 파일명 유지, 한글 파일명 인코딩 처리) */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) throws MalformedURLException {
        AttachedFile file = postService.findFile(fileId);
        Resource resource = new UrlResource(Paths.get(fileStore.getFullPath(file.getStoredName())).toUri());
        String encodedName = UriUtils.encode(file.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
