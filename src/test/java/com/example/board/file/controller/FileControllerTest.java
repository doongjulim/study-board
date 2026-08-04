package com.example.board.file.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.file.store.FileStore;
import com.example.board.post.domain.AttachedFile;
import com.example.board.post.service.PostService;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class FileControllerTest {

    @TempDir
    static Path tempDir;

    @Autowired MockMvc mockMvc;
    @MockBean PostService postService;
    @MockBean TokenService tokenService;
    @MockBean FileStore fileStore;

    @Test
    @DisplayName("GET /files/{id}/view - 이미지 파일은 인라인으로 서빙한다")
    void view_image() throws Exception {
        Path image = tempDir.resolve("stored.png");
        Files.write(image, new byte[]{1, 2, 3});
        given(postService.findFile(1L))
                .willReturn(new AttachedFile("photo.png", "stored.png", "image/png", 3));
        given(fileStore.getFullPath("stored.png")).willReturn(image.toString());

        mockMvc.perform(get("/files/1/view"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    @DisplayName("GET /files/{id}/view - 이미지가 아닌 파일은 다운로드로 돌려보낸다 (XSS 방지)")
    void view_nonImage_redirectsToDownload() throws Exception {
        given(postService.findFile(2L))
                .willReturn(new AttachedFile("evil.html", "stored.html", "text/html", 10));

        mockMvc.perform(get("/files/2/view"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/files/2/download"));
    }

    @Test
    @DisplayName("GET /files/{id}/download - 항상 첨부파일(octet-stream)로 내려준다")
    void download_forcesAttachment() throws Exception {
        Path file = tempDir.resolve("stored.txt");
        Files.write(file, "data".getBytes());
        given(postService.findFile(3L))
                .willReturn(new AttachedFile("메모.txt", "stored.txt", "text/plain", 4));
        given(fileStore.getFullPath("stored.txt")).willReturn(file.toString());

        mockMvc.perform(get("/files/3/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }
}
