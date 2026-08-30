package com.example.board.file.scheduler;

import com.example.board.file.store.FileStore;
import com.example.board.post.repository.AttachedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 어디에서도 참조하지 않는 업로드 파일을 매일 새벽에 치운다.
 *
 * <p>파일은 DB 보다 먼저 저장된다 - 저장에 실패하면 게시글을 만들 이유가 없기 때문이다.
 * 그래서 파일은 올라갔는데 그 뒤 트랜잭션이 되돌아가면, 아무 글도 가리키지 않는 파일이 디스크에 남는다.
 * 한 건씩은 사소하지만 지우는 사람이 아무도 없으므로 계속 쌓이기만 한다.</p>
 *
 * <p><b>유예 시간을 두는 것이 핵심이다.</b> 지금 막 저장됐지만 트랜잭션이 아직 커밋되지 않은 파일은
 * DB 에서 보이지 않는다 - 그 순간에 정리를 돌리면 정상 업로드를 고아로 오해해 지워 버린다.
 * 그래서 "충분히 오래된" 파일만 대상으로 삼는다. 정리가 하루 늦는 것은 아무 문제가 아니지만,
 * 사용자의 첨부파일을 지우는 것은 되돌릴 수 없다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileCleanupScheduler {

    /** 이보다 최근에 만들어진 파일은 아직 트랜잭션 중일 수 있으므로 건드리지 않는다 */
    private static final Duration GRACE_PERIOD = Duration.ofHours(24);

    private final FileStore fileStore;
    private final AttachedFileRepository fileRepository;
    private final Clock clock;

    @Scheduled(cron = "0 40 4 * * *")
    public void deleteOrphanFiles() {
        Instant threshold = clock.instant().minus(GRACE_PERIOD);

        List<FileStore.StoredFileInfo> stored;
        try {
            stored = fileStore.listStoredFiles();
        } catch (IOException e) {
            log.warn("업로드 디렉터리를 읽지 못해 고아 파일 정리를 건너뜁니다.", e);
            return;
        }
        if (stored.isEmpty()) {
            return;
        }

        Set<String> referenced = new HashSet<>(fileRepository.findAllStoredNames());
        int deleted = 0;
        for (FileStore.StoredFileInfo info : stored) {
            if (info.lastModifiedAt().isAfter(threshold) || referenced.contains(info.storedName())) {
                continue;
            }
            fileStore.deleteFile(info.storedName());
            deleted++;
        }
        if (deleted > 0) {
            log.info("어느 글도 참조하지 않는 업로드 파일 {}건을 정리했습니다.", deleted);
        }
    }
}
