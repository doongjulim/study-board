package com.example.board.file.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 디스크에서 파일을 지우되, <b>트랜잭션이 커밋된 뒤에</b> 지운다.
 *
 * <p>왜 필요한가 - 예전에는 게시글 삭제가 이랬다.
 *
 * <pre>
 *   post.getFiles().forEach(f -&gt; fileStore.deleteFile(f.getStoredName()));  // 디스크에서 먼저 지우고
 *   postRepository.delete(post);                                            // 그다음 DB
 * </pre>
 *
 * <p>여기서 뒤의 작업이 실패해 트랜잭션이 되돌아가면, DB 의 행은 그대로 살아 있는데 파일만
 * 사라진다. 화면에는 첨부파일이 있는데 눌러도 없는 상태가 되고, 되돌릴 방법도 없다.
 * DB 는 롤백되지만 파일 시스템은 롤백되지 않기 때문이다.</p>
 *
 * <p>그래서 "지워도 된다" 는 판단만 트랜잭션 안에서 하고, 실제 삭제는 커밋이 확정된 뒤로 미룬다.
 * 반대 방향(커밋은 됐는데 파일 삭제가 실패)은 고아 파일로 남는데, 그건 데이터가 사라지는 것이 아니라
 * 쓰이지 않는 파일이 남는 것이라 {@link com.example.board.file.scheduler.OrphanFileCleanupScheduler}
 * 가 나중에 치울 수 있다. 두 실패 중 감당할 수 있는 쪽을 고른 것이다.</p>
 *
 * <p>트랜잭션 밖에서 불리면 미룰 곳이 없으므로 그 자리에서 지운다.</p>
 */
@Component
@RequiredArgsConstructor
public class TransactionalFileRemover {

    private final FileStore fileStore;

    public void removeAfterCommit(String storedName) {
        if (storedName == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStore.deleteFile(storedName);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStore.deleteFile(storedName);
            }
        });
    }
}
