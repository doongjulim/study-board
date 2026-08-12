package com.example.board.auth.controller;

import com.example.board.auth.dto.PasswordResetForm;
import com.example.board.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 비밀번호 찾기 - 로그인하지 않은 상태에서 쓰는 화면이다 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/password")
public class PasswordResetController {

    /**
     * 가입 여부와 관계없이 같은 안내를 보여 준다.
     * 결과가 달라지면 어떤 이메일이 가입돼 있는지 확인하는 수단이 되기 때문이다.
     */
    private static final String SENT_MESSAGE =
            "입력하신 주소로 재설정 링크를 보냈습니다. 메일이 오지 않는다면 주소를 다시 확인해 주세요.";

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot")
    public String forgotForm() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot")
    public String sendResetLink(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordResetService.sendResetLink(email);
        redirectAttributes.addFlashAttribute("message", SENT_MESSAGE);
        return "redirect:/login";
    }

    /** 메일 링크로 들어오는 자리 - 만료·사용 완료면 폼 대신 안내를 보여 준다 */
    @GetMapping("/reset")
    public String resetForm(@RequestParam(required = false) String token, Model model) {
        if (!passwordResetService.isUsable(token)) {
            return expired(model);
        }
        PasswordResetForm form = new PasswordResetForm();
        form.setToken(token);
        model.addAttribute("passwordResetForm", form);
        return "auth/reset-password";
    }

    @PostMapping("/reset")
    public String reset(@Valid @ModelAttribute PasswordResetForm passwordResetForm,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("newPasswordConfirm") && !passwordResetForm.isConfirmed()) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }
        if (!passwordResetService.reset(passwordResetForm.getToken(), passwordResetForm.getNewPassword())) {
            return expired(model);
        }
        redirectAttributes.addFlashAttribute("message",
                "비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.");
        return "redirect:/login";
    }

    private String expired(Model model) {
        model.addAttribute("message",
                "만료되었거나 이미 사용한 링크입니다. 재설정을 다시 요청해 주세요.");
        return "auth/forgot-password";
    }
}
