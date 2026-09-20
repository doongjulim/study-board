package com.example.board.group.controller;

import com.example.board.auth.MemberPrincipal;
import com.example.board.group.domain.StudyGroup;
import com.example.board.group.dto.GroupForm;
import com.example.board.group.service.CheerService;
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
    /** 그룹의 '구성' 과 그 안에서 오가는 '행동' 은 다른 책임이라 서비스가 나뉘어 있다 */
    private final CheerService cheerService;
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
        // 오늘 이미 응원한 사람 - 화면이 버튼을 잠근다 (사람마다 따로 묻지 않는다)
        model.addAttribute("cheeredToday", cheerService.findCheeredToday(id, principal.id()));
        return "groups/detail";
    }

    /**
     * 응원 보내기.
     *
     * <p>순위는 상위권을 더 뛰게 하지만 하위권을 조용히 떠나게 한다. 응원은 반대 방향으로
     * 작동하는 유일한 버튼이라 순위표 안에 둔다 - 숫자 옆에 있어야 그 숫자를 보고 누른다.</p>
     *
     * <p>이미 오늘 보냈어도 오류로 다루지 않는다 - 두 번 누른 것은 사용자의 잘못이 아니고,
     * 결과는 어느 쪽이든 같다({@code CheerService#send}).</p>
     */
    @PostMapping("/{id}/cheer/{memberId}")
    public String cheer(@PathVariable Long id,
                        @PathVariable Long memberId,
                        @AuthenticationPrincipal MemberPrincipal principal,
                        RedirectAttributes redirectAttributes) {
        boolean sent = cheerService.send(id, principal.id(), memberId);
        redirectAttributes.addFlashAttribute("message",
                sent ? "응원을 보냈습니다 👏" : "오늘은 이미 응원을 보냈어요.");
        return "redirect:/groups/" + id;
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

    // ── 그룹장 전용 관리 ──────────────────────────────────────
    // 권한 검사는 전부 서비스에 있다. 화면에서 버튼을 감추는 것은 안내이지 방어가 아니다.

    /** 이름·소개 수정 폼 */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        StudyGroup group = studyGroupService.findGroupForMember(id, principal.id());
        GroupForm form = new GroupForm();
        // 수정 폼은 화면에 있는 모든 필드를 채워야 한다 - 빠뜨린 필드는 DTO 기본값으로 조용히 덮어써진다
        form.setName(group.getName());
        form.setDescription(group.getDescription());
        model.addAttribute("groupForm", form);
        model.addAttribute("group", group);
        return "groups/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute GroupForm groupForm,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("group", studyGroupService.findGroupForMember(id, principal.id()));
            return "groups/edit";
        }
        studyGroupService.update(id, groupForm, principal.id());
        redirectAttributes.addFlashAttribute("message", "그룹 정보를 수정했습니다.");
        return "redirect:/groups/" + id;
    }

    /** 초대 코드 재발급 - 코드가 새어 나갔을 때의 대응 수단 */
    @PostMapping("/{id}/invite-code")
    public String renewInviteCode(@PathVariable Long id,
                                  @AuthenticationPrincipal MemberPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        String code = studyGroupService.renewInviteCode(id, principal.id());
        redirectAttributes.addFlashAttribute("message",
                "새 초대 코드를 발급했습니다: " + code);
        return "redirect:/groups/" + id;
    }

    /** 멤버 내보내기 */
    @PostMapping("/{id}/members/{memberId}/remove")
    public String removeMember(@PathVariable Long id,
                               @PathVariable Long memberId,
                               @AuthenticationPrincipal MemberPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        studyGroupService.removeMember(id, memberId, principal.id());
        redirectAttributes.addFlashAttribute("message", "멤버를 내보냈습니다.");
        return "redirect:/groups/" + id;
    }
}
