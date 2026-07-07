package com.ulticode.modules.backup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.service.impl.BackupServiceImpl;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BackupService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupServiceTest {

    @Mock
    private BackupMapper backupMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private Clock clock;

    @InjectMocks
    private BackupServiceImpl backupService;

    @TempDir
    Path tempDir;

    private static final String USER_ID = "test-admin-id";
    private static final String BACKUP_ID = "test-backup-id";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "backupDir", tempDir.toString());
        ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:mysql://localhost:3306/testdb");
        ReflectionTestUtils.setField(backupService, "datasourceUsername", "root");
        ReflectionTestUtils.setField(backupService, "datasourcePassword", "password");
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

            // Act
            BackupVO result = backupService.createBackup(USER_ID, dto);

            // Assert
            assertNotNull(result);
            assertEquals(BackupStatus.PENDING, result.getStatus());
            assertEquals(BackupType.FULL, result.getType());
            assertEquals(USER_ID, result.getCreatedBy());
            verify(backupMapper).insert(any(Backup.class));
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
    }

    @Nested
    @DisplayName("getBackups Tests")
    class GetBackupsTests {

        @BeforeEach
        void setUp() {
            lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
            lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        }

        @Test
        @DisplayName("should return paginated backups")
        void shouldReturnPaginatedBackups() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            Page<Backup> mockPage = new Page<>(1, 10);
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup_full_20240101_120000.sql");
            backup.setType(BackupType.FULL);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCreatedBy(USER_ID);
            mockPage.setRecords(List.of(backup));
            mockPage.setTotal(1);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
            when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            // Act
            var result = backupService.getBackups(query);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getItems().size());
        }

        @Test
        @DisplayName("should filter backups by type")
        void shouldFilterBackupsByType() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();
            query.setType(BackupType.FULL);

            Page<Backup> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

            // Act
            backupService.getBackups(query);

            // Assert
            verify(backupMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should filter backups by status")
        void shouldFilterBackupsByStatus() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();
            query.setStatus(BackupStatus.COMPLETED);

            Page<Backup> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

            // Act
            backupService.getBackups(query);

            // Assert
            verify(backupMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("getBackupById Tests")
    class GetBackupByIdTests {

        @Test
        @DisplayName("should return backup when found")
        void shouldReturnBackupWhenFound() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup_full_20240101_120000.sql");
            backup.setType(BackupType.FULL);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCreatedBy(USER_ID);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);

            // Act
            BackupVO result = backupService.getBackupById(BACKUP_ID);

            // Assert
            assertNotNull(result);
            assertEquals(BACKUP_ID, result.getId());
            assertEquals("backup_full_20240101_120000.sql", result.getFilename());
        }

        @Test
        @DisplayName("should throw exception when backup not found")
        void shouldThrowExceptionWhenBackupNotFound() {
            // Arrange
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(null);

            // Act & Assert
            assertThrows(BusinessException.class, () -> backupService.getBackupById(BACKUP_ID));
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

            // Create test file
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

            // Create test file
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
        @DisplayName("should convert Backup to BackupVO correctly")
        void shouldConvertBackupToBackupVOCorrectly() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup_full_20240101_120000.sql");
            backup.setSize(1024L);
            backup.setType(BackupType.FULL);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCreatedBy(USER_ID);

            // Act
            BackupVO result = backupService.toVO(backup);

            // Assert
            assertEquals(BACKUP_ID, result.getId());
            assertEquals("backup_full_20240101_120000.sql", result.getFilename());
            assertEquals(1024L, result.getSize());
            assertEquals(BackupType.FULL, result.getType());
            assertEquals(BackupStatus.COMPLETED, result.getStatus());
            assertEquals(USER_ID, result.getCreatedBy());
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
}
