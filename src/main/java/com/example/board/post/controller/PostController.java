package com.example.board.post.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.post.domain.Post;
import com.example.board.post.dto.PostForm;
import com.example.board.post.dto.PostSummary;
import com.example.board.post.dto.SearchType;
import com.example.board.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    private static final int PAGE_BLOCK_SIZE = 5;

    /** 목록 (검색 + 정렬 + 페이징) */
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false, defaultValue = "TITLE") SearchType searchType,
                       @RequestParam(required = false, defaultValue = "latest") String sort,
                       @PageableDefault(size = 10) Pageable pageable,
                       Model model) {
        Sort order = switch (sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "id");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);

        Page<PostSummary> posts = postService.findAll(keyword, searchType, sorted);

        // 페이지 번호 블록 계산 (1~5, 6~10 …)
        int blockStart = (posts.getNumber() / PAGE_BLOCK_SIZE) * PAGE_BLOCK_SIZE;
        int blockEnd = Math.min(blockStart + PAGE_BLOCK_SIZE - 1, Math.max(posts.getTotalPages() - 1, 0));

        model.addAttribute("posts", posts);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searchType", searchType);
        model.addAttribute("searchTypes", SearchType.values());
        model.addAttribute("sort", sort);
        model.addAttribute("blockStart", blockStart);
        model.addAttribute("blockEnd", blockEnd);
        return "posts/list";
    }

    /** 상세 */
    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.findById(id));
        return "posts/view";
    }

    /** 작성 폼 */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("postForm", new PostForm());
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
