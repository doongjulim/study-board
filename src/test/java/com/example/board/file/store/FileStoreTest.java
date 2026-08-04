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
                "file", "photo.PNG", "image/png", "img".getBytes());

        AttachedFile stored = fileStore.storeFile(multipart);

        assertThat(Path.of(fileStore.getFullPath(stored.getStoredName()))).exists();
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
}
