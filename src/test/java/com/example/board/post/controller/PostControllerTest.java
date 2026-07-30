package com.example.board.post.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.service.CommentService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.SearchType;
import com.example.board.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PostService postService;
    @MockBean CommentService commentService;

    private Post postFixture() {
        return new Post("제목", "내용", new Member("tester1", "encoded-password", "작성자"));
    }

    /** 로그인한 회원(id=1)으로 요청을 보낸다 */
    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(1L, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    // ── GET /posts ────────────────────────────────────────────

    @Test
    @DisplayName("GET /posts - 목록 페이지가 200 을 반환한다")
    void list() throws Exception {
        given(postService.findAll(isNull(), any(SearchType.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/list"))
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    @DisplayName("GET /posts?keyword=spring - keyword 가 모델에 담긴다")
    void list_withKeyword() throws Exception {
        given(postService.findAll(eq("spring"), any(SearchType.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts").param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "spring"));
    }

    @Test
    @DisplayName("GET /posts?searchType=TITLE_CONTENT - 검색 타입이 서비스로 전달된다")
    void list_withSearchType() throws Exception {
        given(postService.findAll(eq("spring"), eq(SearchType.TITLE_CONTENT), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts")
                        .param("keyword", "spring")
                        .param("searchType", "TITLE_CONTENT"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchType", SearchType.TITLE_CONTENT));
    }

    @Test
    @DisplayName("GET /posts?sort=oldest - 정렬 파라미터가 모델에 담긴다")
    void list_withSort() throws Exception {
        given(postService.findAll(isNull(), any(SearchType.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts").param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "oldest"));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404 페이지를 반환한다")
    void view_notFound() throws Exception {
        given(postService.findById(999L))
                .willThrow(new IllegalArgumentException("게시글이 존재하지 않습니다. id=999"));

        mockMvc.perform(get("/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    // ── GET /posts/{id} ───────────────────────────────────────

    @Test
    @DisplayName("GET /posts/{id} - 상세 페이지가 200 을 반환한다")
    void viewDetail() throws Exception {
        given(postService.findById(1L)).willReturn(postFixture());

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/view"))
                .andExpect(model().attributeExists("post"));
    }

    // ── GET /posts/new ────────────────────────────────────────

    @Test
    @DisplayName("GET /posts/new - 로그인 상태면 작성 폼이 200 을 반환한다")
    void createForm() throws Exception {
        mockMvc.perform(get("/posts/new").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attributeExists("postForm"));
    }

    @Test
    @DisplayName("비로그인으로 글쓰기 폼에 접근하면 로그인 페이지로 리다이렉트한다")
    void createForm_requiresLogin() throws Exception {
        mockMvc.perform(get("/posts/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    // ── POST /posts ───────────────────────────────────────────

    @Test
    @DisplayName("POST /posts - 유효한 폼이면 상세 페이지로 리다이렉트한다")
    void create_success() throws Exception {
        given(postService.create(any(PostForm.class), eq(1L))).willReturn(1L);

        mockMvc.perform(multipart("/posts").with(csrf()).with(memberAuth())
                        .param("title", "제목")
                        .param("content", "내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));
    }

    @Test
    @DisplayName("POST /posts - 제목이 없으면 폼으로 돌아온다")
    void create_validationFail() throws Exception {
        mockMvc.perform(multipart("/posts").with(csrf()).with(memberAuth())
                        .param("title", "")
                        .param("content", "내용"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().hasErrors());
    }

    // ── GET /posts/{id}/edit ──────────────────────────────────

    @Test
    @DisplayName("GET /posts/{id}/edit - 수정 폼이 200 을 반환한다")
    void editForm() throws Exception {
        given(postService.findOwned(1L, 1L)).willReturn(postFixture());

        mockMvc.perform(get("/posts/1/edit").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/edit"))
                .andExpect(model().attributeExists("postForm", "post"));
    }

    // ── POST /posts/{id}/edit ─────────────────────────────────

    @Test
    @DisplayName("POST /posts/{id}/edit - 유효한 폼이면 상세 페이지로 리다이렉트한다")
    void edit_success() throws Exception {
        mockMvc.perform(multipart("/posts/1/edit").with(csrf()).with(memberAuth())
                        .param("title", "새제목")
                        .param("content", "새내용"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

        then(postService).should().update(eq(1L), any(PostForm.class), eq(1L));
    }

    @Test
    @DisplayName("POST /posts/{id}/edit - 내용이 없으면 수정 폼으로 돌아온다")
    void edit_validationFail() throws Exception {
        given(postService.findById(1L)).willReturn(postFixture());

        mockMvc.perform(multipart("/posts/1/edit").with(csrf()).with(memberAuth())
                        .param("title", "새제목")
                        .param("content", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/edit"))
                .andExpect(model().hasErrors());
    }

    // ── POST /posts/{id}/delete ───────────────────────────────

    @Test
    @DisplayName("POST /posts/{id}/delete - 삭제 후 목록으로 리다이렉트한다")
    void delete() throws Exception {
        mockMvc.perform(post("/posts/1/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"));

        then(postService).should().delete(1L, 1L);
    }

    // ── POST /posts/{postId}/files/{fileId}/delete ────────────

    @Test
    @DisplayName("POST /posts/{postId}/files/{fileId}/delete - 파일 삭제 후 수정 폼으로 리다이렉트한다")
    void deleteFile() throws Exception {
        mockMvc.perform(post("/posts/1/files/5/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1/edit"));

        then(postService).should().deleteFile(1L, 5L, 1L);
    }
}
