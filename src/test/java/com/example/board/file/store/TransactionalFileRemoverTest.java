package com.example.board.file.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * "지워도 되는가" 는 트랜잭션 안에서 판단하고, "실제로 지우는" 일은 커밋 뒤에 한다.
 *
 * <p>DB 는 롤백되지만 파일 시스템은 롤백되지 않는다. 이 순서를 지키지 않으면
 * 트랜잭션이 되돌아갔을 때 DB 의 행은 살아 있는데 파일만 사라진다 - 화면에는 첨부파일이 보이는데
 * 눌러도 없는 상태가 되고, 되돌릴 방법이 없다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TransactionalFileRemoverTest {

    @Mock FileStore fileStore;
    @InjectMocks TransactionalFileRemover remover;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 전까지 파일을 지우지 않는다")
    void insideTransaction_doesNotDeleteBeforeCommit() {
        TransactionSynchronizationManager.initSynchronization();

        remover.removeAfterCommit("uuid.txt");

        then(fileStore).should(never()).deleteFile("uuid.txt");
    }

    @Test
    @DisplayName("커밋이 확정되면 그때 파일을 지운다")
    void insideTransaction_deletesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        remover.removeAfterCommit("uuid.txt");

        commit();

        then(fileStore).should().deleteFile("uuid.txt");
    }

    @Test
    @DisplayName("트랜잭션 밖에서는 미룰 곳이 없으므로 그 자리에서 지운다")
    void outsideTransaction_deletesImmediately() {
        remover.removeAfterCommit("uuid.txt");

        then(fileStore).should().deleteFile("uuid.txt");
    }

    @Test
    @DisplayName("파일명이 없으면 아무 일도 하지 않는다")
    void nullName_isIgnored() {
        remover.removeAfterCommit(null);

        then(fileStore).should(never()).deleteFile(null);
    }

    /** 트랜잭션 매니저가 커밋 직후에 하는 일을 흉내낸다 */
    private void commit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }
}
