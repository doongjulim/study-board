package com.example.board.file.store;

import com.example.board.file.exception.UnsupportedFileTypeException;
import com.example.board.post.domain.AttachedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FileStoreTest {

    @TempDir
    Path tempDir;

    FileStore fileStore;

    @BeforeEach
    void setUp() throws Exception {
        fileStore = new FileStore();
        Field field = FileStore.class.getDeclaredField("uploadDir");
        field.setAccessible(true);
        field.set(fileStore, tempDir.toString() + "/");
        fileStore.init();
    }

    @Test
    @DisplayName("단일 파일을 저장하면 AttachedFile 이 반환되고 디스크에 파일이 생성된다")
    void storeFile() throws IOException {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello".getBytes());

        AttachedFile stored = fileStore.storeFile(multipart);

        assertThat(stored.getOriginalName()).isEqualTo("hello.txt");
        assertThat(stored.getStoredName()).endsWith(".txt");
        assertThat(stored.getContentType()).isEqualTo("text/plain");
        assertThat(stored.getSize()).isEqualTo(5);
        assertThat(Path.of(fileStore.getFullPath(stored.getStoredName()))).exists();
    }

    @Test
    @DisplayName("확장자가 없는 파일은 형식을 판단할 수 없어 거부한다")
    void storeFile_noExtension_rejected() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "README", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> fileStore.storeFile(multipart))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    @DisplayName("html/svg/실행파일 등 허용 목록 밖의 확장자는 거부한다")
    void storeFile_disallowedExtension_rejected() {
        for (String name : List.of("evil.html", "evil.svg", "evil.exe", "evil.jsp", "evil.sh")) {
            MockMultipartFile multipart = new MockMultipartFile(
                    "file", name, "application/octet-stream", "x".getBytes());

            assertThatThrownBy(() -> fileStore.storeFile(multipart))
                    .as(name)
                    .isInstanceOf(UnsupportedFileTypeException.class);
        }
    }

    @Test
    @DisplayName("대문자 확장자(PNG)도 허용 목록으로 인식해 저장한다")
    void storeFile_uppercaseExtension() throws IOException {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "photo.PNG", "image/png", pngBytes());

        AttachedFile stored = fileStore.storeFile(multipart);

        assertThat(Path.of(fileStore.getFullPath(stored.getStoredName()))).exists();
    }

    // ── 이미지 내용 검사 ──────────────────────────────────────
    //
    // 확장자와 Content-Type 은 둘 다 올리는 쪽이 정하는 값이다. 그런데 이미지는 인라인으로
    // 서빙되므로(/files/{id}/view), "이미지다" 라는 주장만으로 그 경로를 타게 두면 안 된다.
    // 꾸며 낼 수 없는 값은 파일의 내용뿐이라, 이미지 확장자는 앞부분 바이트를 확인한다.

    @Test
    @DisplayName("이미지 확장자인데 내용이 이미지가 아니면 거부한다")
    void storeFile_fakeImage_rejected() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThatThrownBy(() -> fileStore.storeFile(multipart))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    @DisplayName("이미지의 Content-Type 은 보내온 값이 아니라 파일 내용에서 정한다")
    void storeFile_contentTypeComesFromContent() throws IOException {
        // 확장자도 헤더도 jpeg 라고 주장하지만, 내용은 PNG 다
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", pngBytes());

        AttachedFile stored = fileStore.storeFile(multipart);

        assertThat(stored.getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("이미지가 아닌 허용 형식은 보내온 Content-Type 을 그대로 쓴다")
    void storeFile_nonImageKeepsDeclaredType() throws IOException {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.7".getBytes());

        AttachedFile stored = fileStore.storeFile(multipart);

        assertThat(stored.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("내용이 너무 짧아 형식을 알 수 없는 이미지도 거부한다")
    void storeFile_truncatedImage_rejected() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "tiny.png", "image/png", new byte[]{(byte) 0x89, 0x50});

        assertThatThrownBy(() -> fileStore.storeFile(multipart))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    @DisplayName("storeFiles() 는 비어있는 파일을 건너뛴다")
    void storeFiles_skipsEmpty() throws IOException {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);
        MockMultipartFile valid = new MockMultipartFile(
                "file", "a.txt", "text/plain", "data".getBytes());

        List<AttachedFile> result = fileStore.storeFiles(List.of(empty, valid));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalName()).isEqualTo("a.txt");
    }

    @Test
    @DisplayName("storeFiles() 에 null 을 전달하면 빈 리스트를 반환한다")
    void storeFiles_null() throws IOException {
        List<AttachedFile> result = fileStore.storeFiles(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteFile() 은 디스크 파일을 삭제한다")
    void deleteFile() throws IOException {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "del.txt", "text/plain", "bye".getBytes());
        AttachedFile stored = fileStore.storeFile(multipart);
        Path filePath = Path.of(fileStore.getFullPath(stored.getStoredName()));
        assertThat(filePath).exists();

        fileStore.deleteFile(stored.getStoredName());

        assertThat(filePath).doesNotExist();
    }

    @Test
    @DisplayName("존재하지 않는 파일을 deleteFile() 해도 예외가 발생하지 않는다")
    void deleteFile_notExist() {
        assertThatCode(() -> fileStore.deleteFile("nonexistent.txt"))
                .doesNotThrowAnyException();
    }

    // ── 고아 파일 정리를 위한 목록 ────────────────────────────

    @Test
    @DisplayName("listStoredFiles() 는 저장된 파일의 이름과 마지막 수정 시각을 돌려준다")
    void listStoredFiles() throws IOException {
        AttachedFile stored = fileStore.storeFile(
                new MockMultipartFile("file", "a.txt", "text/plain", "data".getBytes()));

        List<FileStore.StoredFileInfo> files = fileStore.listStoredFiles();

        assertThat(files).hasSize(1);
        assertThat(files.get(0).storedName()).isEqualTo(stored.getStoredName());
        assertThat(files.get(0).lastModifiedAt()).isNotNull();
    }

    /** 판정에 쓰이는 것은 앞부분 서명뿐이라, 실제 그림 데이터까지 만들 필요는 없다 */
    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};
    }
}
