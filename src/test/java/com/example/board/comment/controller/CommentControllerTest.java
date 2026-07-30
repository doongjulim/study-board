package com.example.board.comment.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.jwt.JwtAuthenticationFilter;
import com.example.board.auth.jwt.JwtTokenProvider;
import com.example.board.comment.domain.Comment;
import com.example.board.comment.service.CommentService;
import com.example.board.config.SecurityConfig;
import com.example.board.member.domain.Member;
import com.example.board.plan.domain.Plan;
import com.example.board.post.domain.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class CommentControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired MockMvc mockMvc;
    @MockBean CommentService commentService;

    private static RequestPostProcessor memberAuth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new MemberPrincipal(MEMBER_ID, "tester1", "테스터"), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private Member member() {
        Member member = new Member("tester1", "encoded-password", "테스터");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    @Test
    @DisplayName("POST /posts/{id}/comments - 댓글 작성 후 게시글로 돌아간다")
    void addToPost() throws Exception {
        mockMvc.perform(post("/posts/1/comments").with(csrf()).with(memberAuth())
                        .param("content", "좋은 글이네요"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"));

        then(commentService).should().addToPost(1L, MEMBER_ID, "좋은 글이네요");
    }

    @Test
    @DisplayName("POST /posts/{id}/comments - 내용이 비면 저장하지 않고 오류 플래시와 함께 돌아간다")
    void addToPost_blank() throws Exception {
        mockMvc.perform(post("/posts/1/comments").with(csrf()).with(memberAuth())
                        .param("content", "  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/1"))
                .andExpect(flash().attributeExists("commentError"));

        then(commentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /plans/{id}/comments - 댓글 작성 후 공유 플랜 상세로 돌아간다")
    void addToPlan() throws Exception {
        mockMvc.perform(post("/plans/2/comments").with(csrf()).with(memberAuth())
                        .param("content", "화이팅!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/shared/2"));

        then(commentService).should().addToPlan(2L, MEMBER_ID, "화이팅!");
    }

    @Test
    @DisplayName("POST /comments/{id}/delete - 게시글 댓글 삭제 후 게시글로 돌아간다")
    void delete_postComment() throws Exception {
        Post targetPost = new Post("제목", "내용", member());
        ReflectionTestUtils.setField(targetPost, "id", 3L);
        given(commentService.delete(5L, MEMBER_ID))
                .willReturn(Comment.forPost(targetPost, member(), "댓글"));

        mockMvc.perform(post("/comments/5/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/3"));
    }

    @Test
    @DisplayName("POST /comments/{id}/delete - 플랜 댓글 삭제 후 공유 플랜 상세로 돌아간다")
    void delete_planComment() throws Exception {
        Plan targetPlan = new Plan("플랜", null, member(), LocalDate.of(2026, 7, 30), null, null);
        ReflectionTestUtils.setField(targetPlan, "id", 4L);
        given(commentService.delete(6L, MEMBER_ID))
                .willReturn(Comment.forPlan(targetPlan, member(), "댓글"));

        mockMvc.perform(post("/comments/6/delete").with(csrf()).with(memberAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans/shared/4"));
    }

    @Test
    @DisplayName("비로그인으로 댓글을 작성하면 로그인 페이지로 리다이렉트한다")
    void addToPost_requiresLogin() throws Exception {
        mockMvc.perform(post("/posts/1/comments").with(csrf())
                        .param("content", "댓글"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));

        then(commentService).shouldHaveNoInteractions();
    }
}
