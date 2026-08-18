package com.example.board.post.dto;

import com.example.board.post.domain.PostCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class PostForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    private String content;

    /** 고르지 않으면 자유 게시글로 본다 */
    private PostCategory category = PostCategory.FREE;

    private List<MultipartFile> files;
}
