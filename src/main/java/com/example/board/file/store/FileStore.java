package com.example.board.file.store;

import com.example.board.file.exception.UnsupportedFileTypeException;
import com.example.board.post.domain.AttachedFile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 로컬 디스크에 파일을 저장/삭제하는 컴포넌트.
 * 파일명 충돌 방지를 위해 UUID로 저장하고, 원본 이름은 DB에 보관한다.
 * html/svg/실행파일 등을 통한 XSS·악성코드 업로드를 막기 위해 확장자 화이트리스트를 검사한다.
 */
@Slf4j
@Component
public class FileStore {

    /** 허용 확장자 화이트리스트 - 확장자가 없거나 목록에 없으면 업로드를 거부한다 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",                      // 이미지
            "pdf", "txt", "md", "csv",                                 // 문서
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp",        // 오피스
            "zip");                                                    // 압축

    @Value("${file.upload-dir}")
    private String uploadDir;

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(Paths.get(uploadDir));
    }

    public String getFullPath(String storedName) {
        return uploadDir + storedName;
    }

    public List<AttachedFile> storeFiles(List<MultipartFile> multipartFiles) throws IOException {
        List<AttachedFile> result = new ArrayList<>();
        if (multipartFiles == null) {
            return result;
        }
        for (MultipartFile multipartFile : multipartFiles) {
            if (multipartFile != null && !multipartFile.isEmpty()) {
                result.add(storeFile(multipartFile));
            }
        }
        return result;
    }

    public AttachedFile storeFile(MultipartFile multipartFile) throws IOException {
        String originalName = multipartFile.getOriginalFilename();
        validateExtension(originalName);
        String storedName = createStoredName(originalName);
        multipartFile.transferTo(Paths.get(getFullPath(storedName)));
        return new AttachedFile(
                originalName,
                storedName,
                multipartFile.getContentType(),
                multipartFile.getSize()
        );
    }

    public void deleteFile(String storedName) {
        try {
            Files.deleteIfExists(Paths.get(getFullPath(storedName)));
        } catch (IOException e) {
            // 디스크 삭제 실패가 게시글 삭제를 막지 않도록 로그만 남긴다
            log.warn("파일 삭제 실패: {}", storedName, e);
        }
    }

    private void validateExtension(String originalName) {
        String ext = extractExt(originalName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new UnsupportedFileTypeException(originalName);
        }
    }

    private String createStoredName(String originalName) {
        String ext = extractExt(originalName);
        return UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
    }

    private String extractExt(String originalName) {
        if (originalName == null) {
            return "";
        }
        int pos = originalName.lastIndexOf(".");
        return pos == -1 ? "" : originalName.substring(pos + 1);
    }
}
