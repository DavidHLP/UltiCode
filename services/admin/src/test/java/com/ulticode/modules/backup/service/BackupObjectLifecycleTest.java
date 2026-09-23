package com.ulticode.modules.backup.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.service.impl.BackupObjectLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the concrete BackupObjectLifecycle seam.
 *
 * <p>The same-thread executor keeps start deterministic while proving that
 * production dispatch uses the named Admin executor.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupObjectLifecycleTest {

    private static final String BACKUP_ID = "test-backup-id";

    @Mock
    private BackupMapper backupMapper;

    @Mock
    private BackupDeletionTombstoneMapper backupDeletionTombstoneMapper;

    @Mock
    private Clock clock;

    @Mock
    private BackupProcessPort backupProcessPort;

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private Executor adminBackupExecutor;

    private BackupObjectLifecycle lifecycle;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        lifecycle = new BackupObjectLifecycle(
                backupMapper,
                backupDeletionTombstoneMapper,
                clock,
                backupProcessPort,
                fileStorage,
                adminBackupExecutor,
                tempDir.toString(),
                300);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(adminBackupExecutor).execute(any(Runnable.class));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(backupMapper.updateById(any(Backup.class))).thenReturn(1);
        lenient().when(backupMapper.failUnlessCompleted(any(), any(), any(), any())).thenReturn(1);
    }

    private Backup pendingBackup() {
        Backup backup = new Backup();
        backup.setId(BACKUP_ID);
        backup.setFilename("backup_full_test.sql");
        backup.setSize(0L);
        backup.setType(BackupType.FULL);
        backup.setStatus(BackupStatus.PENDING);
        backup.setCreatedBy("test-admin-id");
        return backup;
    }

    /** A detached row as the database would return it, independent of in-memory mutation. */
    private Backup durableRow(BackupStatus status, String objectKey) {
        Backup row = new Backup();
        row.setId(BACKUP_ID);
        row.setFilename("backup_full_test.sql");
        row.setType(BackupType.FULL);
        row.setStatus(status);
        row.setObjectKey(objectKey);
        return row;
    }

    /** Captures the conditional FAILED transition exactly as the database receives it. */
    private FailedTransition capturedFailure() {
        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> completedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(backupMapper).failUnlessCompleted(
                id.capture(), completedAt.capture(), error.capture(), objectKey.capture());
        return new FailedTransition(id.getValue(), completedAt.getValue(), error.getValue(), objectKey.getValue());
    }

    private record FailedTransition(String id, LocalDateTime completedAt, String error, String objectKey) {
    }

    @Nested
    @DisplayName("lifecycle transitions")
    class LifecycleTransitions {

        @Test
        @DisplayName("PENDING -> IN_PROGRESS -> COMPLETED after object upload")
        void shouldCompleteWhenDumpSucceeds() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });

            lifecycle.start(BACKUP_ID);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, atLeast(2)).updateById(captor.capture());
            Backup finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);

            assertEquals(BackupStatus.COMPLETED, finalState.getStatus());
            assertNotNull(finalState.getCompletedAt());
            assertTrue(finalState.getSize() > 0);
            assertNotNull(finalState.getChecksum());
            assertEquals(64, finalState.getChecksum().length());
            assertEquals("admin/backups/2026/01/" + BACKUP_ID + ".sql", finalState.getObjectKey());
            assertNotNull(finalState.getMetadata());
            assertEquals("FULL", finalState.getMetadata().get("backupType"));
            verify(fileStorage).putFile(eq(finalState.getObjectKey()), any(Path.class), eq("application/sql"));
        }

        @Test
        @DisplayName("the planned object key is persisted before the bytes are uploaded")
        void shouldPersistPlannedKeyBeforeUpload() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });

            lifecycle.start(BACKUP_ID);

            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            // The PUT must come after the row already names the planned key.
            InOrder order = inOrder(backupMapper, fileStorage);
            order.verify(backupMapper, times(2)).updateById(any(Backup.class));
            order.verify(fileStorage).putFile(eq(objectKey), any(Path.class), eq("application/sql"));

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, times(3)).updateById(captor.capture());
            // The second write is the planned key; a crash after the PUT leaves
            // a row that names the dump instead of an untracked object.
            assertEquals(objectKey, captor.getAllValues().get(1).getObjectKey());
        }

        @Test
        @DisplayName("upload failure marks backup FAILED and removes temp file")
        void shouldFailWhenObjectUploadFailsAndCleanTempFile() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            AtomicReference<Path> dumpPath = new AtomicReference<>();
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                dumpPath.set(dump);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            doThrow(new RuntimeException("object store unavailable"))
                    .when(fileStorage).putFile(any(), any(Path.class), any());

            lifecycle.start(BACKUP_ID);

            assertEquals(BackupStatus.FAILED, backup.getStatus());
            assertNotNull(backup.getCompletedAt());
            assertFalse(Files.exists(dumpPath.get()));
            verify(fileStorage).putFile(any(), any(Path.class), eq("application/sql"));
        }

        @Test
        @DisplayName("no-op IN_PROGRESS update fails the run before dumping")
        void shouldFailWhenInProgressUpdateAffectsNoRows() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.updateById(any(Backup.class))).thenReturn(0);

            lifecycle.start(BACKUP_ID);

            verify(backupProcessPort, never()).dump(any(Path.class));
            verify(fileStorage, never()).putFile(any(), any(Path.class), any());
            assertEquals(BackupStatus.FAILED, backup.getStatus());
        }

        @Test
        @DisplayName("no-op COMPLETED update fails the run and removes the uploaded object")
        void shouldFailWhenCompletedUpdateAffectsNoRows() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup)
                    .thenReturn(durableRow(BackupStatus.IN_PROGRESS, null));
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            when(backupMapper.updateById(any(Backup.class))).thenReturn(1, 1, 0, 1);

            lifecycle.start(BACKUP_ID);

            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            verify(fileStorage).putFile(eq(objectKey), any(Path.class), eq("application/sql"));
            verify(backupDeletionTombstoneMapper).insert(BACKUP_ID, objectKey);
            verify(fileStorage, never()).delete(any());
            assertEquals(BackupStatus.FAILED, backup.getStatus());
        }
        @Test
        @DisplayName("DB update failure after upload removes the uploaded object")
        void shouldRemoveObjectWhenCompletedStateCannotBePersisted() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup)
                    .thenReturn(durableRow(BackupStatus.IN_PROGRESS, null));
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            when(backupMapper.updateById(any(Backup.class)))
                    .thenReturn(1, 1)
                    .thenThrow(new RuntimeException("database unavailable"));

            lifecycle.start(BACKUP_ID);

            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            verify(fileStorage).putFile(eq(objectKey), any(Path.class), eq("application/sql"));
            verify(backupDeletionTombstoneMapper).insert(BACKUP_ID, objectKey);
            verify(fileStorage, never()).delete(any());
            assertEquals(BackupStatus.FAILED, backup.getStatus());
        }

        @Test
        @DisplayName("ambiguous COMPLETED update that persisted keeps the uploaded object")
        void shouldKeepObjectWhenCompletedStateRacedConnectionError() throws Exception {
            Backup backup = pendingBackup();
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup)
                    .thenReturn(durableRow(BackupStatus.COMPLETED, objectKey));
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            when(backupMapper.updateById(any(Backup.class)))
                    .thenReturn(1, 1)
                    .thenThrow(new RuntimeException("connection reset"));

            lifecycle.start(BACKUP_ID);

            verify(fileStorage).putFile(eq(objectKey), any(Path.class), eq("application/sql"));
            verify(fileStorage, never()).delete(any());
            // exactly the IN_PROGRESS write, the planned key and the raced
            // COMPLETED write; no FAILED overwrite
            verify(backupMapper, times(3)).updateById(any(Backup.class));
        }

        @Test
        @DisplayName("unreadable completion state keeps the uploaded object and still reaches a terminal row")
        void shouldKeepObjectWhenCompletionOutcomeIsUnknown() throws Exception {
            Backup backup = pendingBackup();
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup)
                    .thenThrow(new RuntimeException("database unreachable"));
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            when(backupMapper.updateById(any(Backup.class)))
                    .thenReturn(1, 1)
                    .thenThrow(new RuntimeException("connection reset"));
            when(backupMapper.failUnlessCompleted(any(), any(), any(), any())).thenReturn(1);

            lifecycle.start(BACKUP_ID);

            verify(fileStorage).putFile(eq(objectKey), any(Path.class), eq("application/sql"));
            verify(fileStorage, never()).delete(any());
            // IN_PROGRESS, the planned key and the raced COMPLETED write only:
            // the terminal FAILED transition must not go through the unguarded
            // row update.
            verify(backupMapper, times(3)).updateById(any(Backup.class));
            assertEquals(BackupStatus.FAILED, backup.getStatus());
            assertNotNull(backup.getError());
            assertTrue(backup.getError().contains(objectKey));
            assertEquals(objectKey, capturedFailure().objectKey(),
                    "the preserved object key must reach the durable row");
        }

        @Test
        @DisplayName("a durable COMPLETED row is never replaced by the FAILED transition")
        void shouldNotReplaceDurableCompletedRow() throws Exception {
            Backup backup = pendingBackup();
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup)
                    .thenThrow(new RuntimeException("database unreachable"));
            when(backupProcessPort.dump(any(Path.class))).thenAnswer(invocation -> {
                Path dump = invocation.getArgument(0);
                Files.writeString(dump, "-- fake dump");
                return true;
            });
            when(backupMapper.updateById(any(Backup.class)))
                    .thenReturn(1, 1)
                    .thenThrow(new RuntimeException("connection reset"));
            // The row committed COMPLETED while the failure path was running.
            when(backupMapper.failUnlessCompleted(any(), any(), any(), any())).thenReturn(0);

            lifecycle.start(BACKUP_ID);

            verify(fileStorage, never()).delete(any());
            assertEquals(objectKey, capturedFailure().objectKey());
        }

        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump reports failure")
        void shouldFailWhenDumpReportsFailure() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any())).thenReturn(false);

            lifecycle.start(BACKUP_ID);

            verify(backupMapper).updateById(any(Backup.class));
            FailedTransition failure = capturedFailure();

            assertEquals(BackupStatus.FAILED, backup.getStatus(),
                    "backup must reach FAILED when dump reports failure");
            assertNotNull(failure.completedAt());
            assertNotNull(failure.error(), "failure must capture an error message");
        }

        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump succeeds but file is missing")
        void shouldFailWhenFileMissingAfterDump() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            // Dump reports success but never creates the file
            when(backupProcessPort.dump(any())).thenReturn(true);

            lifecycle.start(BACKUP_ID);

            verify(backupMapper).updateById(any(Backup.class));
            FailedTransition failure = capturedFailure();

            assertEquals(BackupStatus.FAILED, backup.getStatus(),
                    "backup must reach FAILED when the file is missing after dump");
            assertNotNull(failure.error());
        }
        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump throws, error message captured")
        void shouldFailWhenDumpThrows() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any())).thenThrow(new RuntimeException("mysqldump not on PATH"));

            lifecycle.start(BACKUP_ID);

            verify(backupMapper).updateById(any(Backup.class));
            FailedTransition failure = capturedFailure();

            assertEquals(BackupStatus.FAILED, backup.getStatus());
            assertEquals("mysqldump not on PATH", failure.error(),
                    "exception message must be captured on the FAILED record");
        }
    }

    @Nested
    @DisplayName("missing-record handling")
    class MissingRecord {

        @Test
        @DisplayName("does nothing when the backup record does not exist")
        void shouldNoopWhenBackupMissing() {
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(null);

            lifecycle.start(BACKUP_ID);

            verify(backupMapper, never()).updateById(any(Backup.class));
            verifyNoInteractions(backupProcessPort);
        }
    }
    @Nested
    @DisplayName("dispatch, delete and sweep")
    class DispatchDeleteAndSweep {

        @Test
        @DisplayName("rejected execution records FAILED before rethrowing")
        void shouldRecordImmediateRejectionBeforeRethrowing() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.failUnlessCompleted(any(), any(), any(), any())).thenReturn(1);
            doThrow(new RejectedExecutionException("executor is shutting down"))
                    .when(adminBackupExecutor).execute(any(Runnable.class));

            assertThrows(RejectedExecutionException.class, () -> lifecycle.start(BACKUP_ID));

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(backupMapper).failUnlessCompleted(
                    eq(BACKUP_ID), any(), error.capture(), eq(null));
            assertEquals(BackupStatus.PENDING, backup.getStatus(),
                    "the rejected state write uses the guarded mapper contract");
            assertTrue(error.getValue().contains("executor is shutting down"));
        }

        @Test
        @DisplayName("a saturated ThreadPoolTaskExecutor records FAILED before rethrowing")
        void shouldRecordSpringTaskRejectionBeforeRethrowing() throws InterruptedException {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            ThreadPoolTaskExecutor saturated = new ThreadPoolTaskExecutor();
            saturated.setCorePoolSize(1);
            saturated.setMaxPoolSize(1);
            saturated.setQueueCapacity(0);
            saturated.initialize();
            CountDownLatch occupied = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            saturated.execute(() -> {
                occupied.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                assertTrue(occupied.await(5, TimeUnit.SECONDS), "worker must be busy before dispatch");
                BackupObjectLifecycle realExecutorLifecycle = new BackupObjectLifecycle(
                        backupMapper,
                        backupDeletionTombstoneMapper,
                        clock,
                        backupProcessPort,
                        fileStorage,
                        saturated,
                        tempDir.toString(),
                        300);
                // ThreadPoolTaskExecutor wraps refusal in Spring's TaskRejectedException,
                // which extends RejectedExecutionException and must still be recorded.
                assertThrows(TaskRejectedException.class,
                        () -> realExecutorLifecycle.start(BACKUP_ID));

                ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
                verify(backupMapper).failUnlessCompleted(
                        eq(BACKUP_ID), any(), error.capture(), eq(null));
                assertTrue(error.getValue().contains("Backup execution rejected"),
                        "production rejection must mark the row FAILED, got: " + error.getValue());
            } finally {
                release.countDown();
                saturated.shutdown();
            }
        }

        @Test
        @DisplayName("running rows with planned keys cannot be deleted")
        void shouldRejectDeleteOfRunningPlannedObject() {
            Backup backup = pendingBackup();
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            assertThrows(BusinessException.class, () -> lifecycle.delete(BACKUP_ID));

            verify(backupMapper, never()).deleteIfNotRunning(any());
            verifyNoInteractions(fileStorage);
        }
        @Test
        @DisplayName("invalid object keys are rejected before row deletion")
        void shouldRejectInvalidObjectKey() {
            Backup backup = pendingBackup();
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setObjectKey("app/avatars/not-a-backup-key");
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            assertThrows(BusinessException.class, () -> lifecycle.delete(BACKUP_ID));

            verify(backupMapper, never()).deleteIfNotRunning(any());
            verifyNoInteractions(fileStorage);
        }


        @Test
        @DisplayName("keyless legacy rows delete without touching storage")
        void shouldDeleteKeylessLegacyRow() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteIfNotRunning(BACKUP_ID)).thenReturn(1);

            lifecycle.delete(BACKUP_ID);
            verify(backupMapper).deleteIfNotRunning(BACKUP_ID);
            verify(backupDeletionTombstoneMapper).insert(BACKUP_ID, null);
            verifyNoInteractions(fileStorage);
        }

        @Test
        @DisplayName("row deletion and tombstone happen before after-commit cleanup")
        void shouldDeleteRowAndTombstoneBeforeObjectCleanup() {
            Backup backup = pendingBackup();
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setFilename("backup_full_test.sql");
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            backup.setObjectKey(objectKey);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteIfNotRunning(BACKUP_ID)).thenReturn(1);
            when(backupDeletionTombstoneMapper.markObjectDeleted(objectKey)).thenReturn(1);

            TransactionSynchronizationManager.initSynchronization();
            try {
                lifecycle.delete(BACKUP_ID);

                verify(backupMapper).deleteIfNotRunning(BACKUP_ID);
                verify(backupDeletionTombstoneMapper).insert(BACKUP_ID, objectKey);
                verifyNoInteractions(fileStorage);

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
                verify(fileStorage, timeout(5000)).delete(objectKey);
                verify(backupDeletionTombstoneMapper, timeout(5000)).markObjectDeleted(objectKey);
                // Object cleanup must never occupy the dedicated backup
                // executor: a busy single worker would reject an unrelated
                // backup submission.
                verify(adminBackupExecutor, never()).execute(any());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
        @Test
        @DisplayName("rolled-back delete transaction never deletes storage bytes")
        void shouldNotDeleteObjectWhenDeleteTransactionRollsBack() {
            Backup backup = pendingBackup();
            backup.setStatus(BackupStatus.COMPLETED);
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            backup.setObjectKey(objectKey);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteIfNotRunning(BACKUP_ID)).thenReturn(1);

            TransactionSynchronizationManager.initSynchronization();
            try {
                lifecycle.delete(BACKUP_ID);

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
                verifyNoInteractions(fileStorage);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }


        @Test
        @DisplayName("tombstone failure never deletes storage bytes")
        void shouldPreserveObjectWhenTombstoneInsertFails() {
            Backup backup = pendingBackup();
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteIfNotRunning(BACKUP_ID)).thenReturn(1);
            doThrow(new IllegalStateException("database unavailable"))
                    .when(backupDeletionTombstoneMapper).insert(any(), any());

            assertThrows(IllegalStateException.class, () -> lifecycle.delete(BACKUP_ID));

            verifyNoInteractions(fileStorage);
        }

        @Test
        @DisplayName("FAILED rows wait for the age-gated sweep")
        void shouldWaitForSweepWhenDeletingFailedRow() {
            Backup backup = pendingBackup();
            backup.setStatus(BackupStatus.FAILED);
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            backup.setObjectKey(objectKey);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteIfNotRunning(BACKUP_ID)).thenReturn(1);
            when(backupDeletionTombstoneMapper.selectPendingObjectKeys(100, 300))
                    .thenReturn(List.of(objectKey));

            lifecycle.delete(BACKUP_ID);

            verifyNoInteractions(fileStorage);
            lifecycle.sweep();
            verify(fileStorage).delete(objectKey);
        }

        @Test
        @DisplayName("sweep stops without storage access when tombstone read fails")
        void shouldNotDeleteWhenSweepReadFails() {
            when(backupDeletionTombstoneMapper.selectPendingObjectKeys(100, 300))
                    .thenThrow(new RuntimeException("database unavailable"));

            assertEquals(0, lifecycle.sweep());

            verifyNoInteractions(fileStorage);
        }
        @Test
        @DisplayName("object deletion remains retryable when tombstone completion fails")
        void shouldLeaveTombstoneForRetryAfterMarkFailure() {
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            when(backupDeletionTombstoneMapper.selectPendingObjectKeys(100, 300))
                    .thenReturn(List.of(objectKey));
            doThrow(new RuntimeException("database unavailable"))
                    .when(backupDeletionTombstoneMapper).markObjectDeleted(objectKey);

            assertEquals(1, lifecycle.sweep());

            verify(fileStorage).delete(objectKey);
            verify(backupDeletionTombstoneMapper).markObjectDeleted(objectKey);
            verify(backupDeletionTombstoneMapper, never())
                    .recordObjectDeleteFailure(any(), any());
        }


        @Test
        @DisplayName("storage failure leaves the tombstone pending with bounded error")
        void shouldRecordBoundedStorageFailure() {
            String objectKey = "admin/backups/2026/01/" + BACKUP_ID + ".sql";
            when(backupDeletionTombstoneMapper.selectPendingObjectKeys(100, 300))
                    .thenReturn(List.of(objectKey));
            doThrow(new RuntimeException("x".repeat(900))).when(fileStorage).delete(objectKey);

            assertEquals(1, lifecycle.sweep());

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(backupDeletionTombstoneMapper).recordObjectDeleteFailure(eq(objectKey), error.capture());
            assertEquals(500, error.getValue().length());
            verify(backupDeletionTombstoneMapper, never()).markObjectDeleted(any());
        }

        @Test
        @DisplayName("sweep is the sole scheduled lifecycle entry point")
        void shouldKeepOneScheduledSweepSeam() throws NoSuchMethodException {
            assertNotNull(BackupObjectLifecycle.class.getMethod("sweep")
                    .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class));
        }
    }
}
