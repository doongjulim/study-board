package com.example.board.comment.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.comment.domain.Comment;
import com.example.board.comment.dto.CommentForm;
import com.example.board.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 게시글 댓글 작성 */
    @PostMapping("/posts/{postId}/comments")
    public String addToPost(@PathVariable Long postId,
                            @Valid @ModelAttribute CommentForm commentForm,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError",
                    bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/posts/" + postId;
        }
        commentService.addToPost(postId, principal.id(), commentForm.getContent());
        return "redirect:/posts/" + postId;
    }

    /** 공유 플랜 댓글 작성 */
    @PostMapping("/plans/{planId}/comments")
    public String addToPlan(@PathVariable Long planId,
                            @Valid @ModelAttribute CommentForm commentForm,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("commentError",
                    bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/plans/shared/" + planId;
        }
        commentService.addToPlan(planId, principal.id(), commentForm.getContent());
        return "redirect:/plans/shared/" + planId;
    }

    /** 댓글 삭제 (본인만) - 삭제 후 원래 화면으로 돌아간다 */
    @PostMapping("/comments/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal) {
        Comment deleted = commentService.delete(id, principal.id());
        if (deleted.isForPost()) {
            return "redirect:/posts/" + deleted.getPost().getId();
        }
        return "redirect:/plans/shared/" + deleted.getPlan().getId();
    }
}
