package com.example.board.post.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.service.CommentService;
import com.example.board.auth.AuthCookies;
import com.example.board.auth.service.TokenService;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.post.domain.Post;
import com.example.board.post.domain.PostCategory;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.SearchType;
import com.example.board.post.service.PostService;
import com.example.board.stats.domain.WeeklyReport;
import com.example.board.stats.service.WeeklyReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookies.class})
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PostService postService;
    @MockBean TokenService tokenService;
    @MockBean CommentService commentService;
    @MockBean WeeklyReportService weeklyReportService;

    /** 상세 화면이 부르는 댓글 조회. 스레드가 없어도 화면은 그려져야 한다 */
    private void stubEmptyComments() {
        given(commentService.findForPost(anyLong(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(commentService.countForPost(anyLong())).willReturn(0L);
    }

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
        given(postService.findAll(isNull(), any(SearchType.class), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/list"))
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    @DisplayName("GET /posts?keyword=spring - keyword 가 모델에 담긴다")
    void list_withKeyword() throws Exception {
        given(postService.findAll(eq("spring"), any(SearchType.class), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts").param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("keyword", "spring"));
    }

    @Test
    @DisplayName("GET /posts?searchType=TITLE_CONTENT - 검색 타입이 서비스로 전달된다")
    void list_withSearchType() throws Exception {
        given(postService.findAll(eq("spring"), eq(SearchType.TITLE_CONTENT), any(), any(Pageable.class)))
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
        given(postService.findAll(isNull(), any(SearchType.class), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts").param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "oldest"));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404 페이지를 반환한다")
    void view_notFound() throws Exception {
        given(postService.read(eq(999L), any(), anyBoolean()))
                .willThrow(new IllegalArgumentException("게시글이 존재하지 않습니다. id=999"));

        mockMvc.perform(get("/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    @DisplayName("GET /posts?category=QUESTION - 분류를 그대로 서비스에 넘긴다")
    void listFiltersByCategory() throws Exception {
        given(postService.findAll(any(), any(SearchType.class), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/posts").param("category", "QUESTION"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("category", PostCategory.QUESTION))
                .andExpect(model().attributeExists("categories", "likedPostIds"));

        then(postService).should().findAll(any(), any(SearchType.class),
                eq(PostCategory.QUESTION), any(Pageable.class));
    }

    @Test
    @DisplayName("POST /posts/{id}/like - 좋아요를 전환하고 상세로 돌아간다")
    void toggleLike() throws Exception {
        mockMvc.perform(post("/posts/1/like").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

        then(postService).should().toggleLike(1L, 1L);
    }

    @Test
    @DisplayName("POST /posts/{id}/like - 로그인 없이는 누를 수 없다")
    void likeRequiresLogin() throws Exception {
        mockMvc.perform(post("/posts/1/like").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));

        then(postService).should(never()).toggleLike(any(), any());
    }

    // ── GET /posts/{id} ───────────────────────────────────────

    @Test
    @DisplayName("GET /posts/{id} - 상세 페이지가 200 을 반환한다")
    void viewDetail() throws Exception {
        given(postService.read(eq(1L), any(), anyBoolean())).willReturn(postFixture());
        stubEmptyComments();

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/view"))
                .andExpect(model().attributeExists("post", "contentHtml", "liked"));
    }

    @Test
    @DisplayName("GET /posts/{id} - 본문의 마크다운은 살균된 HTML 로 모델에 담긴다")
    void detailSanitizesMarkdown() throws Exception {
        Post dangerous = new Post("제목", "**굵게**<script>alert(1)</script>", postFixture().getAuthor());
        given(postService.read(eq(2L), any(), anyBoolean())).willReturn(dangerous);
        stubEmptyComments();

        mockMvc.perform(get("/posts/2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("contentHtml",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("<strong>굵게</strong>"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script")))));
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
    @DisplayName("GET /posts/new?week=... - 주간 학습 기록으로 제목·본문 초안을 채운다")
    void createForm_weeklyDraft() throws Exception {
        given(weeklyReportService.draft(1L, java.time.LocalDate.of(2026, 8, 3)))
                .willReturn(new WeeklyReport("[8/3~8/9] 이번 주 학습 인증", "이번 주 완료율 67% (4/6)"));

        mockMvc.perform(get("/posts/new").param("week", "2026-08-03").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/form"))
                .andExpect(model().attribute("postForm",
                        org.hamcrest.Matchers.hasProperty("title",
                                org.hamcrest.Matchers.is("[8/3~8/9] 이번 주 학습 인증"))))
                .andExpect(model().attribute("postForm",
                        org.hamcrest.Matchers.hasProperty("content",
                                org.hamcrest.Matchers.containsString("완료율 67%"))));
    }

    @Test
    @DisplayName("GET /posts/new - week 파라미터가 없으면 빈 폼을 준다")
    void createForm_withoutWeek() throws Exception {
        mockMvc.perform(get("/posts/new").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postForm",
                        org.hamcrest.Matchers.hasProperty("title", org.hamcrest.Matchers.nullValue())));

        then(weeklyReportService).shouldHaveNoInteractions();
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

    @Test
    @DisplayName("GET /posts/{id}/edit - 원래 분류가 폼에 실린다 - 안 실으면 오타만 고쳐도 '자유' 로 바뀐다")
    void editForm_carriesCurrentCategory() throws Exception {
        Post question = new Post("제목", "내용",
                new Member("tester1", "encoded-password", "작성자"), PostCategory.QUESTION);
        given(postService.findOwned(1L, 1L)).willReturn(question);

        mockMvc.perform(get("/posts/1/edit").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("postForm",
                        hasProperty("category", equalTo(PostCategory.QUESTION))));
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

    // ── 모아보기 ──────────────────────────────────────────────
    //
    // 게시판 검색에 얹지 않고 화면을 따로 둔다. 닉네임 검색으로 대신하면 동명이인이 섞이고
    // 닉네임을 바꾸는 순간 내 글이 사라진다 - 이건 검색이 아니라 '내 것' 이다.

    @Test
    @DisplayName("GET /posts/mine - 기본은 내가 쓴 글이다")
    void mine_written() throws Exception {
        given(postService.findMine(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(postService.findLikedPostIds(eq(1L), any())).willReturn(Set.of());

        mockMvc.perform(get("/posts/mine").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(view().name("posts/mine"))
                .andExpect(model().attribute("tab", "written"));

        then(postService).should().findMine(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /posts/mine?tab=liked - 좋아요한 글로 바뀐다")
    void mine_liked() throws Exception {
        given(postService.findLiked(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(postService.findLikedPostIds(eq(1L), any())).willReturn(Set.of());

        mockMvc.perform(get("/posts/mine").param("tab", "liked").with(memberAuth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("tab", "liked"));

        then(postService).should().findLiked(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("모르는 tab 값은 '내가 쓴 글' 로 본다")
    void mine_unknownTab() throws Exception {
        given(postService.findMine(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(postService.findLikedPostIds(eq(1L), any())).willReturn(Set.of());

        mockMvc.perform(get("/posts/mine").param("tab", "아무거나").with(memberAuth()))
                .andExpect(model().attribute("tab", "written"));
    }

    @Test
    @DisplayName("비로그인은 모아보기를 볼 수 없다 - '내 것' 이라는 개념이 성립하지 않는다")
    void mine_requiresLogin() throws Exception {
        mockMvc.perform(get("/posts/mine"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    // ── 마크다운 미리보기 ──────────────────────────────────────

    @Test
    @DisplayName("POST /posts/preview - 마크다운을 HTML 로 돌려준다")
    void preview() throws Exception {
        mockMvc.perform(post("/posts/preview").with(csrf()).with(memberAuth())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("**굵게**"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>굵게</strong>")));
    }

    @Test
    @DisplayName("미리보기도 본문과 같은 살균을 거친다 - 여기만 통과시키면 그게 곧 XSS 다")
    void preview_sanitizes() throws Exception {
        mockMvc.perform(post("/posts/preview").with(csrf()).with(memberAuth())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("<script>alert(1)</script>\n\n# 제목"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script"))))
                .andExpect(content().string(containsString("<h1>제목</h1>")));
    }

    @Test
    @DisplayName("빈 내용을 미리보면 빈 결과가 온다")
    void preview_empty() throws Exception {
        mockMvc.perform(post("/posts/preview").with(csrf()).with(memberAuth())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인은 미리보기를 쓸 수 없다 - 글쓰기는 로그인이 필요하다")
    void preview_requiresLogin() throws Exception {
        mockMvc.perform(post("/posts/preview").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("**굵게**"))
                .andExpect(status().is3xxRedirection());
    }

}
