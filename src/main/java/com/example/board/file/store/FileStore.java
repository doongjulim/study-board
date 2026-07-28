package com.example.board.file.store;

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
import java.util.UUID;

/**
 * 로컬 디스크에 파일을 저장/삭제하는 컴포넌트.
 * 파일명 충돌 방지를 위해 UUID로 저장하고, 원본 이름은 DB에 보관한다.
 */
@Slf4j
@Component
public class FileStore {

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
