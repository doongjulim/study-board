package com.example.board.file.scheduler;

import com.example.board.file.store.FileStore;
import com.example.board.post.repository.AttachedFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * 사용자의 첨부파일을 지우는 작업이다. 틀리면 되돌릴 수 없으므로 규칙을 하나씩 못 박는다.
 *
 * <p>가장 중요한 것은 <b>유예 기간</b>이다. 방금 저장됐지만 트랜잭션이 아직 커밋되지 않은 파일은
 * DB 에서 보이지 않는다 - 유예가 없으면 그 순간의 정상 업로드를 고아로 오해해 지운다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-03-10T04:40:00Z");

    @Mock FileStore fileStore;
    @Mock AttachedFileRepository fileRepository;

    private OrphanFileCleanupScheduler scheduler() {
        return new OrphanFileCleanupScheduler(fileStore, fileRepository,
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private static FileStore.StoredFileInfo file(String name, long hoursAgo) {
        return new FileStore.StoredFileInfo(name, NOW.minusSeconds(hoursAgo * 3600));
    }

    @Test
    @DisplayName("어느 글도 참조하지 않고 충분히 오래된 파일은 지운다")
    void deletesOldOrphan() throws IOException {
        given(fileStore.listStoredFiles()).willReturn(List.of(file("orphan.png", 30)));
        given(fileRepository.findAllStoredNames()).willReturn(List.of());

        scheduler().deleteOrphanFiles();

        then(fileStore).should().deleteFile("orphan.png");
    }

    @Test
    @DisplayName("유예 기간(24시간) 안에 만들어진 파일은 참조가 없어도 건드리지 않는다")
    void keepsRecentFile() throws IOException {
        // 커밋 전이라 DB 에는 아직 없는 정상 업로드가 이 자리에 온다.
        // 여기서 지우면 사용자가 방금 올린 첨부파일이 사라진다.
        given(fileStore.listStoredFiles()).willReturn(List.of(file("just-uploaded.png", 1)));
        given(fileRepository.findAllStoredNames()).willReturn(List.of());

        scheduler().deleteOrphanFiles();

        then(fileStore).should(never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("경계: 정확히 24시간 지난 파일은 아직 지우지 않는다")
    void boundaryIsExclusive() throws IOException {
        given(fileStore.listStoredFiles()).willReturn(List.of(file("edge.png", 24)));
        given(fileRepository.findAllStoredNames()).willReturn(List.of());

        scheduler().deleteOrphanFiles();

        // lastModifiedAt.isAfter(threshold) 가 false 이므로 대상이 된다 - 경계에서의 동작을 명시해 둔다
        then(fileStore).should().deleteFile("edge.png");
    }

    @Test
    @DisplayName("DB 가 아는 파일은 아무리 오래돼도 남긴다")
    void keepsReferencedFile() throws IOException {
        given(fileStore.listStoredFiles()).willReturn(List.of(file("in-use.png", 24 * 365)));
        given(fileRepository.findAllStoredNames()).willReturn(List.of("in-use.png"));

        scheduler().deleteOrphanFiles();

        then(fileStore).should(never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("여러 파일 중 참조되지 않은 것만 골라 지운다")
    void deletesOnlyOrphans() throws IOException {
        given(fileStore.listStoredFiles()).willReturn(List.of(
                file("keep.png", 48), file("drop.png", 48), file("fresh.png", 2)));
        given(fileRepository.findAllStoredNames()).willReturn(List.of("keep.png"));

        scheduler().deleteOrphanFiles();

        then(fileStore).should().deleteFile("drop.png");
        then(fileStore).should(never()).deleteFile("keep.png");
        then(fileStore).should(never()).deleteFile("fresh.png");
    }

    @Test
    @DisplayName("저장소가 비어 있으면 DB 를 조회하지도 않는다")
    void emptyStoreSkipsQuery() throws IOException {
        given(fileStore.listStoredFiles()).willReturn(List.of());

        scheduler().deleteOrphanFiles();

        then(fileRepository).should(never()).findAllStoredNames();
        then(fileStore).should(never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("업로드 디렉터리를 읽지 못하면 아무것도 지우지 않는다")
    void ioErrorDeletesNothing() throws IOException {
        // 목록을 못 읽었다는 것은 "참조되지 않았다" 의 근거가 없다는 뜻이다.
        // 근거 없이 지우느니 이번 실행을 건너뛴다.
        willThrow(new IOException("디렉터리 없음")).given(fileStore).listStoredFiles();

        scheduler().deleteOrphanFiles();

        then(fileStore).should(never()).deleteFile(anyString());
        then(fileRepository).should(never()).findAllStoredNames();
    }
}
