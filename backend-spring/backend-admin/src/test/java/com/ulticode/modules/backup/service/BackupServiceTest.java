package com.ulticode.modules.backup.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
    private Clock clock;

    @Mock
    private BackupProcessPort backupProcessPort;

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
        ReflectionTestUtils.setField(backupService, "backupDir", tempDir.toString());
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
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
        @DisplayName("should return file when backup is completed")
        void shouldReturnFileWhenBackupIsCompleted() throws IOException {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");
            backup.setStatus(BackupStatus.COMPLETED);

            Path testFile = tempDir.resolve("test_backup.sql");
            Files.writeString(testFile, "test sql content");

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act
            File result = backupService.getBackupFile(BACKUP_ID);

            // Assert
            assertNotNull(result);
            assertTrue(result.exists());
            assertEquals("test_backup.sql", result.getName());
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
        @DisplayName("should throw exception when file does not exist")
        void shouldThrowExceptionWhenFileDoesNotExist() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("non_existent.sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act & Assert
            assertThrows(BusinessException.class, () -> backupService.getBackupFile(BACKUP_ID));
        }
    }

    @Nested
    @DisplayName("deleteBackup Tests")
    class DeleteBackupTests {

        @Test
        @DisplayName("should delete backup and file")
        void shouldDeleteBackupAndFile() throws IOException {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("test_backup.sql");

            Path testFile = tempDir.resolve("test_backup.sql");
            Files.writeString(testFile, "test sql content");

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupMapper.deleteById(BACKUP_ID)).thenReturn(1);

            // Act
            backupService.deleteBackup(BACKUP_ID);

            // Assert
            verify(backupMapper).deleteById(BACKUP_ID);
            assertFalse(Files.exists(testFile));
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
        @DisplayName("should throw exception when backup file not found")
        void shouldThrowExceptionWhenBackupFileNotFound() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("non_existent.sql");
            backup.setStatus(BackupStatus.COMPLETED);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act & Assert
            assertThrows(BusinessException.class, () -> backupService.restoreBackup(BACKUP_ID, USER_ID));
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
