package com.example.board.post.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.dto.CommentForm;
import com.example.board.comment.service.CommentService;
import com.example.board.common.markdown.MarkdownRenderer;
import com.example.board.common.web.PageBlock;
import com.example.board.post.domain.Post;
import com.example.board.post.domain.PostCategory;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.dto.SearchType;
import com.example.board.post.service.PostService;
import com.example.board.stats.domain.WeeklyReport;
import com.example.board.stats.service.WeeklyReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    /** 주간 인증글 초안 생성을 위한 읽기 전용 의존 */
    private final WeeklyReportService weeklyReportService;

    /** 분류 선택지는 목록·작성·수정 화면이 모두 쓰므로 한곳에서 채운다 */
    @ModelAttribute("categories")
    public PostCategory[] categories() {
        return PostCategory.values();
    }

    /** 목록 (검색 + 정렬 + 페이징) */
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false, defaultValue = "TITLE") SearchType searchType,
                       @RequestParam(required = false) PostCategory category,
                       @RequestParam(required = false, defaultValue = "latest") String sort,
                       @PageableDefault(size = 10) Pageable pageable,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model) {
        Sort order = switch (sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            case "popular" -> Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);

        Page<PostSummary> posts = postService.findAll(keyword, searchType, category, sorted);
        Long viewerId = (principal != null) ? principal.id() : null;

        model.addAttribute("posts", posts);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searchType", searchType);
        model.addAttribute("searchTypes", SearchType.values());
        model.addAttribute("category", category);
        model.addAttribute("sort", sort);
        model.addAttribute("likedPostIds", postService.findLikedPostIds(viewerId, posts.getContent()));
        model.addAttribute("pageBlock", PageBlock.of(posts));
        return "posts/list";
    }

    /** 상세 - 여는 김에 조회수를 올린다 (작성자 본인의 조회는 세지 않는다) */
    @GetMapping("/{id}")
    public String view(@PathVariable Long id,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model) {
        Long viewerId = (principal != null) ? principal.id() : null;
        Post post = postService.read(id, viewerId);

        model.addAttribute("post", post);
        model.addAttribute("contentHtml", MarkdownRenderer.toSafeHtml(post.getContent()));
        model.addAttribute("liked", postService.hasLiked(id, viewerId));
        model.addAttribute("comments", commentService.findForPost(id));
        model.addAttribute("commentForm", new CommentForm());
        return "posts/view";
    }

    /** 좋아요 켜기·끄기 */
    @PostMapping("/{id}/like")
    public String toggleLike(@PathVariable Long id,
                             @AuthenticationPrincipal MemberPrincipal principal) {
        postService.toggleLike(id, principal.id());
        return "redirect:/posts/" + id;
    }

    /**
     * 작성 폼.
     * week 파라미터가 있으면 그 주의 학습 기록으로 제목·본문 초안을 채워 준다 (플래너 → 인증글 연동).
     */
    @GetMapping("/new")
    public String createForm(@RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             Model model) {
        PostForm form = new PostForm();
        if (week != null) {
            WeeklyReport report = weeklyReportService.draft(principal.id(), week);
            form.setTitle(report.title());
            form.setContent(report.content());
        }
        model.addAttribute("postForm", form);
        return "posts/form";
    }

    /** 작성 처리 */
    @PostMapping
    public String create(@Valid @ModelAttribute PostForm postForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) throws IOException {
        if (bindingResult.hasErrors()) {
            return "posts/form";
        }
        Long id = postService.create(postForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "게시글이 등록되었습니다.");
        return "redirect:/posts/" + id;
    }

    /** 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        Post post = postService.findOwned(id, principal.id());
        PostForm form = new PostForm();
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        model.addAttribute("postForm", form);
        model.addAttribute("post", post);
        return "posts/edit";
    }

    /** 수정 처리 */
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute PostForm postForm,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model,
                       RedirectAttributes redirectAttributes) throws IOException {
        if (bindingResult.hasErrors()) {
            model.addAttribute("post", postService.findById(id));
            return "posts/edit";
        }
        postService.update(id, postForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "게시글이 수정되었습니다.");
        return "redirect:/posts/" + id;
    }

    /** 삭제 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        postService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "게시글이 삭제되었습니다.");
        return "redirect:/posts";
    }

    /** 첨부파일 개별 삭제 (수정 화면에서 사용) */
    @PostMapping("/{postId}/files/{fileId}/delete")
    public String deleteFile(@PathVariable Long postId, @PathVariable Long fileId,
                             @AuthenticationPrincipal MemberPrincipal principal) {
        postService.deleteFile(postId, fileId, principal.id());
        return "redirect:/posts/" + postId + "/edit";
    }
}
