package com.example.board.group.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.service.StudyGroupService;
import com.example.board.stats.service.GroupStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/groups")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;
    /**
     * 그룹 안에서 서로의 진도를 보여 주기 위한 읽기 전용 의존 (일간 뷰가 D-Day 를 읽는 것과 같은 모양).
     * 서비스 계층의 의존은 stats → group 한 방향으로만 두고, 화면 조립만 여기서 한다.
     */
    private final GroupStatsService groupStatsService;
    private final Clock clock;

    /** 내 그룹 목록 + 만들기·초대 코드 가입 폼 - 항목이 적어 한 화면에서 처리한다 */
    @GetMapping
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("groups", studyGroupService.findMyGroups(principal.id()));
        if (!model.containsAttribute("groupForm")) {
            model.addAttribute("groupForm", new GroupForm());
        }
        return "groups/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute GroupForm groupForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("groups", studyGroupService.findMyGroups(principal.id()));
            return "groups/list";
        }
        Long groupId = studyGroupService.create(groupForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "그룹을 만들었습니다. 초대 코드를 스터디원에게 알려 주세요.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/join")
    public String join(@RequestParam String inviteCode,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        try {
            StudyGroup group = studyGroupService.join(inviteCode, principal.id());
            redirectAttributes.addFlashAttribute("message",
                    "'" + group.getName() + "' 그룹에 가입했습니다.");
            return "redirect:/groups/" + group.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 코드 오타는 흔한 일이라 에러 페이지 대신 목록에서 다시 시도하게 한다
            redirectAttributes.addFlashAttribute("joinError", e.getMessage());
            return "redirect:/groups";
        }
    }

    /** 그룹 상세 - 멤버 목록과 초대 코드. 멤버가 아니면 서비스가 403 을 던진다 */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model) {
        StudyGroup group = studyGroupService.findGroupForMember(id, principal.id());
        LocalDate today = LocalDate.now(clock);

        model.addAttribute("group", group);
        model.addAttribute("members", studyGroupService.findMembers(id));
        model.addAttribute("isOwner", group.isOwnedBy(principal.id()));
        model.addAttribute("ranking", groupStatsService.weeklyRanking(id, principal.id(), today));
        model.addAttribute("challenge", groupStatsService.weeklyChallenge(id, today));
        return "groups/detail";
    }

    @PostMapping("/{id}/leave")
    public String leave(@PathVariable Long id,
                        @AuthenticationPrincipal MemberPrincipal principal,
                        RedirectAttributes redirectAttributes) {
        studyGroupService.leave(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "그룹에서 나갔습니다.");
        return "redirect:/groups";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        studyGroupService.delete(id, principal.id());
        redirectAttributes.addFlashAttribute("message", "그룹을 삭제했습니다.");
        return "redirect:/groups";
    }
}
