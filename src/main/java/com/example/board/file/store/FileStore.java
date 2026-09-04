package com.example.board.file.store;

import com.example.board.file.exception.UnsupportedFileTypeException;
import com.example.board.post.domain.AttachedFile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    /**
     * 이 확장자로 올라온 파일은 내용까지 확인한다.
     *
     * <p>이미지만 검사하는 이유는 이미지만 인라인으로 서빙되기 때문이다
     * (/files/{id}/view - 그 밖의 형식은 다운로드로 돌려보낸다).
     * 브라우저가 그 자리에서 해석하는 형식이라 "실제로 이미지인가" 가 곧 보안 경계가 된다.</p>
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

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

    /**
     * 이미지만 받는다 (프로필 사진).
     *
     * <p>일반 첨부와 달리 확장자 화이트리스트 전체를 열지 않는다 - 프로필 자리에 zip 이나 hwp 가
     * 올라올 이유가 없고, 좁게 받을수록 판단할 것이 줄어든다.
     * 내용 검사는 {@link #storeFile} 이 이미 하므로 여기서는 확장자만 좁힌다.</p>
     */
    public AttachedFile storeImage(MultipartFile multipartFile) throws IOException {
        String ext = extractExt(multipartFile.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            throw new UnsupportedFileTypeException(multipartFile.getOriginalFilename());
        }
        return storeFile(multipartFile);
    }

    public AttachedFile storeFile(MultipartFile multipartFile) throws IOException {
        String originalName = multipartFile.getOriginalFilename();
        String ext = extractExt(originalName).toLowerCase(Locale.ROOT);
        validateExtension(originalName, ext);

        // 이미지는 올린 쪽의 주장(Content-Type)이 아니라 파일 내용에서 형식을 정한다
        String contentType = IMAGE_EXTENSIONS.contains(ext)
                ? detectImageType(multipartFile, originalName)
                : multipartFile.getContentType();

        String storedName = createStoredName(originalName);
        multipartFile.transferTo(Paths.get(getFullPath(storedName)));
        return new AttachedFile(originalName, storedName, contentType, multipartFile.getSize());
    }

    /**
     * 이미지 확장자로 올라온 파일의 실제 형식.
     *
     * <p>내용이 이미지가 아니면 거부한다 - {@code evil.png} 라는 이름의 HTML 이 인라인으로
     * 내려가는 길을 확장자와 Content-Type 만으로는 막을 수 없기 때문이다.</p>
     */
    private String detectImageType(MultipartFile multipartFile, String originalName) throws IOException {
        byte[] head = new byte[ImageContentType.HEAD_LENGTH];
        int read;
        try (InputStream in = multipartFile.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        return ImageContentType.detect(read == head.length ? head : Arrays.copyOf(head, read))
                .map(ImageContentType::mediaType)
                .orElseThrow(() -> new UnsupportedFileTypeException(originalName));
    }

    /**
     * 저장소에 실제로 들어 있는 파일들.
     *
     * <p>고아 파일(업로드는 됐는데 DB 에는 남지 않은 파일) 정리에만 쓴다.
     * {@link Path} 가 아니라 이름과 시각만 돌려주는 이유는, 이 인터페이스를 S3 구현으로 바꿔도
     * 정리 로직이 그대로 동작해야 하기 때문이다 - 로컬 경로는 여기서 끝난다.</p>
     */
    public List<StoredFileInfo> listStoredFiles() throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(FileStore::toInfo)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(StoredFileInfo::storedName))
                    .toList();
        }
    }

    private static StoredFileInfo toInfo(Path path) {
        try {
            return new StoredFileInfo(path.getFileName().toString(),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            // 훑는 도중 사라진 파일은 정리 대상에서 빼면 그만이다 - 다음 실행에서 다시 본다
            log.debug("파일 정보를 읽지 못해 건너뜁니다: {}", path, e);
            return null;
        }
    }

    /** 저장소에 있는 파일 하나의 최소 정보 */
    public record StoredFileInfo(String storedName, Instant lastModifiedAt) {
    }

    public void deleteFile(String storedName) {
        try {
            Files.deleteIfExists(Paths.get(getFullPath(storedName)));
        } catch (IOException e) {
            // 디스크 삭제 실패가 게시글 삭제를 막지 않도록 로그만 남긴다
            log.warn("파일 삭제 실패: {}", storedName, e);
        }
    }

    private void validateExtension(String originalName, String ext) {
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
