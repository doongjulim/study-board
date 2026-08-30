package com.example.board.notification.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.common.web.PageBlock;
import com.example.board.notification.domain.Notification;
import com.example.board.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 알림 전체 보기 화면.
 *
 * <p>헤더의 벨 패널은 최근 10건만 띄운다. 그 뒤로 밀려난 알림을 볼 곳이 없어서
 * "댓글이 달렸다" 는 소식이 하루만 지나면 사라지곤 했다. 여기가 그 뒤를 받는다.</p>
 *
 * <p>같은 일을 하는 JSON 엔드포인트가 {@link NotificationController} 에도 있다.
 * 그쪽은 벨 패널의 fetch 가 쓰고, 여기는 화면의 폼이 쓴다 - 판단은 둘 다
 * {@link NotificationService} 한 곳에 있고 여기는 주소와 리다이렉트만 맡는다.
 * 경로를 {@code /notifications/all} 아래로 모은 것은 JSON 쪽과 겹치지 않게 하기 위해서다.</p>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications/all")
public class NotificationPageController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(@PageableDefault(size = 20) Pageable pageable,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model) {
        Page<Notification> notifications = notificationService.findPage(principal.id(), pageable);
        model.addAttribute("notifications", notifications);
        model.addAttribute("pageBlock", PageBlock.of(notifications));
        model.addAttribute("unreadCount", notificationService.countUnread(principal.id()));
        return "notification/list";
    }

    @PostMapping("/{id}/read")
    public String read(@PathVariable Long id,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAsRead(principal.id(), id);
        return redirectToPage(page);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(defaultValue = "0") int page,
                         @AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.delete(principal.id(), id);
        return redirectToPage(page);
    }

    @PostMapping("/read-all")
    public String readAll(@RequestParam(defaultValue = "0") int page,
                          @AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAllAsRead(principal.id());
        return redirectToPage(page);
    }

    /** 조작하고 나서 보던 자리로 돌아온다 - 3페이지에서 하나 지웠는데 1페이지로 튀면 다시 찾아 들어가야 한다 */
    private String redirectToPage(int page) {
        return "redirect:/notifications/all?page=" + Math.max(page, 0);
    }
}
