package com.ulticode.modules.backup.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.projection.BackupReadProjection;
import com.ulticode.modules.backup.service.BackupExecutionService;
import com.ulticode.modules.backup.service.impl.BackupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BackupServiceImpl}. The list / detail read paths
 * moved to {@link BackupReadProjection} &mdash; see
 * {@code BackupReadProjectionTest} for those. This suite focuses on the
 * write side: create, restore, delete, file-download and the public
 * {@link BackupServiceImpl#toVO(Backup)} delegate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupServiceTest {

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
    private BackupExecutionService backupExecutionService;

    @Mock
    private BackupReadProjection backupReadProjection;

    @InjectMocks
    private BackupServiceImpl backupService;

    @TempDir
    Path tempDir;

    private static final String USER_ID = "test-admin-id";
    private static final String BACKUP_ID = "test-backup-id";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "backupTempDir", tempDir.toString());
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(backupMapper.updateById(any(Backup.class))).thenReturn(1);
    }

    @Nested
    @DisplayName("createBackup Tests")
    class CreateBackupTests {

        @BeforeEach
        void setUp() {
            lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
            lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        }

        @Test
        @DisplayName("should create backup with PENDING status")
        void shouldCreateBackupWithPendingStatus() {
            // Arrange
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);

            when(backupMapper.insert(any(Backup.class))).thenAnswer(invocation -> {
                Backup backup = invocation.getArgument(0);
                backup.setId(BACKUP_ID);
                return 1;
            });
            when(backupReadProjection.toVO(any(Backup.class))).thenAnswer(invocation -> {
                Backup source = invocation.getArgument(0);
                BackupVO vo = new BackupVO();
                vo.setId(source.getId());
                vo.setFilename(source.getFilename());
                vo.setType(source.getType());
                vo.setStatus(source.getStatus());
                vo.setCreatedBy(source.getCreatedBy());
                return vo;
            });

            // Act
            BackupVO result = backupService.createBackup(USER_ID, dto);

            // Assert
            assertNotNull(result);
            assertEquals(BackupStatus.PENDING, result.getStatus());
            assertEquals(BackupType.FULL, result.getType());
            assertEquals(USER_ID, result.getCreatedBy());
            verify(backupMapper).insert(any(Backup.class));
            verify(backupReadProjection).toVO(any(Backup.class));
        }

        @Test
        @DisplayName("should create backup with correct filename format")
        void shouldCreateBackupWithCorrectFilenameFormat() {
            // Arrange
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            when(backupMapper.insert(captor.capture())).thenReturn(1);

            // Act
            backupService.createBackup(USER_ID, dto);

            // Assert
            Backup savedBackup = captor.getValue();
            assertTrue(savedBackup.getFilename().startsWith("backup_full_"));
            assertTrue(savedBackup.getFilename().endsWith(".sql"));
        }

        @Test
        @DisplayName("should create INCREMENTAL backup with correct type")
        void shouldCreateIncrementalBackupWithCorrectType() {
            // Arrange
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.INCREMENTAL);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            when(backupMapper.insert(captor.capture())).thenReturn(1);

            // Act
            backupService.createBackup(USER_ID, dto);

            // Assert
            Backup savedBackup = captor.getValue();
            assertEquals(BackupType.INCREMENTAL, savedBackup.getType());
            assertTrue(savedBackup.getFilename().startsWith("backup_incremental_"));
        }

        /**
         * Dispatch-separation wiring test: createBackup must route the
         * async run through the injected BackupExecutionService bean, not
         * via in-class self-invocation. The previous shape called
         * {@code this.executeBackup(id)} directly, which bypassed the AOP
         * proxy and silently defeated {@code @Async}. This assertion fails
         * the day someone reintroduces the self-call.
         */
        @Test
        @DisplayName("should dispatch execution via BackupExecutionService (proxy seam)")
        void shouldDispatchViaBackupExecutionService() {
            // Arrange
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);
            when(backupMapper.insert(any(Backup.class))).thenAnswer(invocation -> {
                Backup backup = invocation.getArgument(0);
                backup.setId(BACKUP_ID);
                return 1;
            });

            // Act
            backupService.createBackup(USER_ID, dto);

            // Assert — dispatched through the injected bean, never in-class.
            verify(backupExecutionService).executeBackup(BACKUP_ID);
            // The orchestration service no longer owns the lifecycle: it
            // must not update the status itself on the create path.
            verify(backupMapper, never()).updateById(any(Backup.class));
        }

        @Test
        @DisplayName("should mark the backup failed when async dispatch is rejected")
        void shouldMarkBackupFailedWhenAsyncDispatchIsRejected() {
            // Arrange
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);
            when(backupMapper.insert(any(Backup.class))).thenAnswer(invocation -> {
                Backup backup = invocation.getArgument(0);
                backup.setId(BACKUP_ID);
                return 1;
            });
            doThrow(new RejectedExecutionException("executor is shutting down"))
                    .when(backupExecutionService).executeBackup(BACKUP_ID);

            // Act
            assertThrows(RejectedExecutionException.class,
                    () -> backupService.createBackup(USER_ID, dto));

            // Assert
            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper).updateById(captor.capture());
            Backup failed = captor.getValue();
            assertEquals(BackupStatus.FAILED, failed.getStatus());
            assertNotNull(failed.getCompletedAt());
            assertTrue(failed.getError().contains("executor is shutting down"));
        }

        /**
         * Proxy-seam regression test: the write service must not declare
         * {@code executeBackup} on its interface. Declaring it there would
         * tempt callers back into a self-call. If this fails, the lifecycle
         * method has leaked back into BackupService.
         */
        @Test
        @DisplayName("BackupService interface must not expose executeBackup")
        void backupServiceInterfaceMustNotExposeExecuteBackup() throws NoSuchMethodException {
            // Act & Assert
            assertThrows(NoSuchMethodException.class,
                    () -> BackupService.class.getMethod("executeBackup", String.class),
                    "executeBackup must live only on BackupExecutionService so the @Async proxy seam is preserved");
        }
    }

    @Nested
    @DisplayName("getBackupFile Tests")
    class GetBackupFileTests {

        @Test
        @DisplayName("should stream the object when backup is completed")
        void shouldStreamWhenBackupIsCompleted() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.of(
                    new FileStoragePort.StorageStream(
                            new ByteArrayInputStream("test sql content".getBytes()),
                            15L,
                            "application/sql")));

            BackupService.BackupDownload result = backupService.getBackupFile(BACKUP_ID);

            assertNotNull(result);
            assertEquals("test_backup.sql", result.filename());
            assertEquals(15L, result.contentLength());
            assertEquals("application/sql", result.contentType());
        }

        @Test
        @DisplayName("should throw exception when backup is not completed")
        void shouldThrowExceptionWhenBackupIsNotCompleted() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setStatus(BackupStatus.PENDING);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> backupService.getBackupFile(BACKUP_ID));
            assertTrue(exception.getMessage().contains("not completed"));
        }

        @Test
        @DisplayName("should throw exception when backup object does not exist")
        void shouldThrowExceptionWhenBackupObjectDoesNotExist() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("non_existent.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> backupService.getBackupFile(BACKUP_ID));
        }
    }

    @Nested
    @DisplayName("deleteBackup Tests")
    class DeleteBackupTests {

        @Test
        @DisplayName("should delete the row before cleaning its object after commit")
        void shouldDeleteBackupRowBeforeObjectCleanup() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteById(BACKUP_ID)).thenReturn(1);

            TransactionSynchronizationManager.initSynchronization();
            try {
                backupService.deleteBackup(BACKUP_ID);

                verify(backupMapper).deleteById(BACKUP_ID);
                verify(backupDeletionTombstoneMapper).insert(BACKUP_ID);
                verify(fileStorage, never()).delete(anyString());

                TransactionSynchronizationManager.getSynchronizations()
                        .forEach(TransactionSynchronization::afterCommit);
                verify(fileStorage, timeout(1000)).delete(backup.getObjectKey());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        @Test
        @DisplayName("should delete a keyless backup row without attempting object deletion")
        void shouldDeleteKeylessBackupRow() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("abandoned_backup.sql");
            backup.setStatus(BackupStatus.PENDING);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteById(BACKUP_ID)).thenReturn(1);

            backupService.deleteBackup(BACKUP_ID);

            verify(fileStorage, never()).delete(anyString());
            verify(backupMapper).deleteById(BACKUP_ID);
            verify(backupDeletionTombstoneMapper).insert(BACKUP_ID);
        }

        @Test
        @DisplayName("should fail when backup row deletion affects no rows")
        void shouldFailWhenBackupRowDeletionAffectsNoRows() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteById(BACKUP_ID)).thenReturn(0);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> backupService.deleteBackup(BACKUP_ID));

            assertTrue(exception.getMessage().contains("Failed to delete backup record"));
            verify(fileStorage, never()).delete(anyString());
        }
        @Test
        @DisplayName("should preserve the object when row deletion throws")
        void shouldPreserveObjectWhenRowDeletionThrows() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            doThrow(new IllegalStateException("database unavailable"))
                    .when(backupMapper).deleteById(BACKUP_ID);

            assertThrows(IllegalStateException.class, () -> backupService.deleteBackup(BACKUP_ID));

            verify(fileStorage, never()).delete(anyString());
        }


        @Test
        @DisplayName("should throw exception when backup not found")
        void shouldThrowExceptionWhenBackupNotFound() {
            // Arrange
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class, () -> backupService.deleteBackup(BACKUP_ID));
        }
    }

    @Nested
    @DisplayName("toVO Tests")
    class ToVOTests {

        @Test
        @DisplayName("should delegate to BackupReadProjection.toVO")
        void shouldDelegateToProjection() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup_full_20240101_120000.sql");
            backup.setSize(1024L);
            backup.setType(BackupType.FULL);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCreatedBy(USER_ID);

            BackupVO projected = new BackupVO();
            projected.setId(BACKUP_ID);
            projected.setFilename("backup_full_20240101_120000.sql");
            projected.setSize(1024L);
            projected.setType(BackupType.FULL);
            projected.setStatus(BackupStatus.COMPLETED);
            projected.setCreatedBy(USER_ID);
            when(backupReadProjection.toVO(backup)).thenReturn(projected);

            // Act
            BackupVO result = backupService.toVO(backup);

            // Assert
            assertSame(projected, result, "service.toVO must delegate to projection.toVO");
            verify(backupReadProjection).toVO(backup);
        }
    }

    @Nested
    @DisplayName("restoreBackup Tests")
    class RestoreBackupTests {

        @Test
        @DisplayName("should throw exception when backup not found")
        void shouldThrowExceptionWhenBackupNotFound() {
            // Arrange
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class, () -> backupService.restoreBackup(BACKUP_ID, USER_ID));
        }

        @Test
        @DisplayName("should throw exception when backup is not completed")
        void shouldThrowExceptionWhenBackupIsNotCompleted() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setStatus(BackupStatus.PENDING);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> backupService.restoreBackup(BACKUP_ID, USER_ID));
            assertTrue(exception.getMessage().contains("non-completed"));
        }

        @Test
        @DisplayName("should throw exception when backup object is not found")
        void shouldThrowExceptionWhenBackupObjectNotFound() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("non_existent.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> backupService.restoreBackup(BACKUP_ID, USER_ID));
        }

        @Test
        @DisplayName("should restore from a secure temp file and clean it up")
        void shouldRestoreFromTempFileAndCleanUp() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setStatus(BackupStatus.COMPLETED);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.of(
                    new FileStoragePort.StorageStream(
                            new ByteArrayInputStream("restore sql".getBytes()),
                            11L,
                            "application/sql")));
            when(backupProcessPort.restore(any(Path.class))).thenAnswer(invocation -> {
                Path path = invocation.getArgument(0);
                assertTrue(Files.exists(path));
                assertEquals(
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                        Files.getPosixFilePermissions(path));
                return true;
            });
            when(backupReadProjection.toVO(backup)).thenReturn(new BackupVO());

            backupService.restoreBackup(BACKUP_ID, USER_ID);

            ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
            verify(backupProcessPort).restore(pathCaptor.capture());
            assertFalse(Files.exists(pathCaptor.getValue()));
        }
        @Test
        @DisplayName("should report partial success when metadata update affects no rows")
        void shouldReportPartialSuccessWhenMetadataUpdateAffectsNoRows() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setStatus(BackupStatus.COMPLETED);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.of(
                    new FileStoragePort.StorageStream(
                            new ByteArrayInputStream("restore sql".getBytes()),
                            11L,
                            "application/sql")));
            when(backupProcessPort.restore(any(Path.class))).thenReturn(true);
            when(backupMapper.updateById(any(Backup.class))).thenReturn(0);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> backupService.restoreBackup(BACKUP_ID, USER_ID));

            assertTrue(exception.getMessage().contains("restore completed"));
            assertTrue(exception.getMessage().contains("metadata"));
            assertFalse(exception.getMessage().contains("Database restore failed"));
            verify(backupProcessPort, times(1)).restore(any(Path.class));
        }

        @Test
        @DisplayName("should reject a corrupted object before database restore")
        void shouldRejectChecksumMismatchBeforeRestore() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup.sql");
            backup.setObjectKey("admin/backups/2026/01/" + BACKUP_ID + ".sql");
            backup.setChecksum("0".repeat(64));
            backup.setStatus(BackupStatus.COMPLETED);
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(fileStorage.openStream(backup.getObjectKey())).thenReturn(Optional.of(
                    new FileStoragePort.StorageStream(
                            new ByteArrayInputStream("restore sql".getBytes()),
                            11L,
                            "application/sql")));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> backupService.restoreBackup(BACKUP_ID, USER_ID));

            assertTrue(exception.getMessage().contains("checksum mismatch"));
            verify(backupProcessPort, never()).restore(any(Path.class));
        }
    }
    @Nested
    @DisplayName("Path Traversal Validation Tests")
    class PathTraversalTests {

        @Test
        @DisplayName("should reject backup filename containing parent path segment")
        void shouldRejectFilenameWithParentPath() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("../etc/passwd");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupService.getBackupFile(BACKUP_ID));
            assertTrue(ex.getMessage().contains("Invalid backup filename"));
        }

        @Test
        @DisplayName("should reject backup filename containing forward slash")
        void shouldRejectFilenameWithForwardSlash() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("sub/dir.sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupService.deleteBackup(BACKUP_ID));
            assertTrue(ex.getMessage().contains("Invalid backup filename"));
        }

        @Test
        @DisplayName("should reject backup filename containing backslash")
        void shouldRejectFilenameWithBackslash() {
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("sub\\dir.sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupService.restoreBackup(BACKUP_ID, USER_ID));
            assertTrue(ex.getMessage().contains("Invalid backup filename"));
        }
    }
}
