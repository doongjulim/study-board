package com.example.board.member.controller;

import com.example.board.auth.AuthCookies;
import com.example.board.auth.MemberPrincipal;
import com.example.board.auth.exception.LoginFailedException;
import com.example.board.member.domain.Member;
import com.example.board.member.dto.NotificationSettingForm;
import com.example.board.member.dto.PasswordChangeForm;
import com.example.board.member.dto.ProfileForm;
import com.example.board.member.exception.DuplicateMemberException;
import com.example.board.member.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.example.board.file.exception.UnsupportedFileTypeException;

import java.io.IOException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 마이페이지 - 프로필·목표 시간·비밀번호·탈퇴 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/me")
public class MyPageController {

    /** 캘린더 구독 주소를 만들 때 쓴다 (비밀번호 재설정 링크와 같은 설정값) */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final MemberService memberService;
    private final AuthCookies authCookies;

    @GetMapping
    public String myPage(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        prepare(model, memberService.findActive(principal.id()));
        return "member/my-page";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute ProfileForm profileForm,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal MemberPrincipal principal,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Member member = memberService.findActive(principal.id());
        if (bindingResult.hasErrors()) {
            return backToPage(model, member);
        }
        try {
            memberService.updateProfile(principal.id(), profileForm);
        } catch (DuplicateMemberException e) {
            bindingResult.rejectValue(e.getField(), "duplicate", e.getMessage());
            return backToPage(model, member);
        }
        redirectAttributes.addFlashAttribute("message", "프로필을 수정했습니다.");
        return "redirect:/me";
    }

    /** 프로필 사진 올리기 */
    @PostMapping("/profile-image")
    public String changeProfileImage(@RequestParam("image") MultipartFile image,
                                     @AuthenticationPrincipal MemberPrincipal principal,
                                     RedirectAttributes redirectAttributes) throws IOException {
        try {
            memberService.changeProfileImage(principal.id(), image);
            redirectAttributes.addFlashAttribute("message", "프로필 사진을 바꿨습니다.");
        } catch (IllegalArgumentException | UnsupportedFileTypeException e) {
            // 사진 하나 때문에 오류 화면으로 보내지 않는다 - 마이페이지에서 이유만 알려 준다
            redirectAttributes.addFlashAttribute("message",
                    e instanceof UnsupportedFileTypeException
                            ? "이미지 파일(jpg·png·gif·webp)만 올릴 수 있습니다."
                            : e.getMessage());
        }
        return "redirect:/me";
    }

    @PostMapping("/profile-image/delete")
    public String removeProfileImage(@AuthenticationPrincipal MemberPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        memberService.removeProfileImage(principal.id());
        redirectAttributes.addFlashAttribute("message", "프로필 사진을 지웠습니다.");
        return "redirect:/me";
    }

    @PostMapping("/notifications")
    public String updateNotificationSetting(@Valid @ModelAttribute NotificationSettingForm notificationSettingForm,
                                            BindingResult bindingResult,
                                            @AuthenticationPrincipal MemberPrincipal principal,
                                            Model model,
                                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return backToPage(model, memberService.findActive(principal.id()));
        }
        memberService.updateNotificationSetting(principal.id(), notificationSettingForm);
        redirectAttributes.addFlashAttribute("message", "알림 설정을 저장했습니다.");
        return "redirect:/me";
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute PasswordChangeForm passwordChangeForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal MemberPrincipal principal,
                                 Model model,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        Member member = memberService.findActive(principal.id());

        if (!bindingResult.hasFieldErrors("newPasswordConfirm") && !passwordChangeForm.isConfirmed()) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (bindingResult.hasErrors()) {
            return backToPage(model, member);
        }
        try {
            memberService.changePassword(principal.id(), passwordChangeForm);
        } catch (LoginFailedException e) {
            bindingResult.rejectValue("currentPassword", "mismatch", "현재 비밀번호가 올바르지 않습니다.");
            return backToPage(model, member);
        }

        // 다른 기기 세션까지 끊었으므로 이 브라우저도 다시 로그인해야 한다
        authCookies.clear(response);
        redirectAttributes.addFlashAttribute("message",
                "비밀번호를 변경했습니다. 모든 기기에서 로그아웃되었으니 다시 로그인해 주세요.");
        return "redirect:/login";
    }

    /**
     * 탈퇴. 확인 값은 계정 종류가 정한다 - 일반 계정은 비밀번호, 소셜 계정은 닉네임
     * (소셜 계정의 비밀번호는 어떤 입력과도 일치하지 않아, 물어봐야 통과할 수 없다).
     */
    @PostMapping("/withdraw")
    public String withdraw(@RequestParam("confirmation") String confirmation,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           HttpServletResponse response,
                           RedirectAttributes redirectAttributes) {
        try {
            memberService.withdraw(principal.id(), confirmation);
        } catch (LoginFailedException e) {
            redirectAttributes.addFlashAttribute("message", "확인 값이 일치하지 않아 탈퇴하지 못했습니다.");
            return "redirect:/me";
        }
        authCookies.clear(response);
        redirectAttributes.addFlashAttribute("message", "탈퇴가 완료되었습니다. 그동안 고생 많으셨어요.");
        return "redirect:/login";
    }

    /** 검증 실패로 화면을 다시 그릴 때, 사용자가 입력한 폼은 유지하고 나머지만 채운다 */
    private String backToPage(Model model, Member member) {
        fillMissingForms(model, member);
        model.addAttribute("member", member);
        model.addAttribute("baseUrl", baseUrl);
        return "member/my-page";
    }

    private void prepare(Model model, Member member) {
        model.addAttribute("member", member);
        // 구독 주소는 외부 앱에 붙여 넣는 값이라 전체 URL 이어야 한다
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("profileForm", toForm(member));
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        model.addAttribute("notificationSettingForm",
                NotificationSettingForm.from(member.getNotificationPreference()));
    }

    private void fillMissingForms(Model model, Member member) {
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", toForm(member));
        }
        if (!model.containsAttribute("passwordChangeForm")) {
            model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        }
        if (!model.containsAttribute("notificationSettingForm")) {
            model.addAttribute("notificationSettingForm",
                    NotificationSettingForm.from(member.getNotificationPreference()));
        }
    }

    private ProfileForm toForm(Member member) {
        ProfileForm form = new ProfileForm();
        form.setNickname(member.getNickname());
        form.setEmail(member.getEmail());
        form.setDailyGoalMinutes(member.getDailyGoalMinutes());
        return form;
    }
}
