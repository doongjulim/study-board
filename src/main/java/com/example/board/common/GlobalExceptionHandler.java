package com.example.board.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 존재하지 않는 게시글/파일 조회 등 → 404 페이지 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e, Model model) {
        log.warn("잘못된 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "error/404";
    }

    /** 업로드 용량 초과 → 목록으로 리다이렉트 + 안내 메시지 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException e,
                                      RedirectAttributes redirectAttributes) {
        log.warn("업로드 용량 초과", e);
        redirectAttributes.addFlashAttribute("message",
                "업로드 용량을 초과했습니다. (파일당 10MB, 요청당 50MB 이하)");
        return "redirect:/posts";
    }

    /** 그 외 예상치 못한 오류 → 500 페이지 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e, Model model) {
        log.error("서버 오류", e);
        model.addAttribute("message", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        return "error/500";
    }
}
