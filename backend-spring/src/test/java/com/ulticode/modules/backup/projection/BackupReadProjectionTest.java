package com.ulticode.modules.backup.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.backup.dto.BackupQueryDTO;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.UserLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultBackupReadProjection}. Verifies that:
 *
 * <ul>
 *   <li>{@code listBackups} builds the expected filter chain and propagates
 *       the {@code createdByName} enrichment from the user-lookup port.</li>
 *   <li>{@code getById} throws on missing backups and enriches when found.</li>
 *   <li>{@code toVO} / {@code toVOList} shape entities faithfully and survive
 *       null / empty enrichment maps.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BackupReadProjectionTest {

    @Mock
    private BackupMapper backupMapper;

    @Mock
    private UserLookupPort userLookupPort;

    @InjectMocks
    private DefaultBackupReadProjection projection;

    private static final String BACKUP_ID = "test-backup-id";
    private static final String USER_ID = "test-admin-id";

    @Nested
    @DisplayName("listBackups Tests")
    class ListBackupsTests {

        @BeforeEach
        void setUp() {
            when(userLookupPort.findUsernamesByIds(any())).thenReturn(Collections.emptyMap());
        }

        @Test
        @DisplayName("should return paginated backups with createdByName enrichment")
        void shouldReturnPaginatedBackupsWithEnrichment() {
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

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);
            when(userLookupPort.findUsernamesByIds(List.of(USER_ID)))
                    .thenReturn(Map.of(USER_ID, "admin"));

            // Act
            var result = projection.listBackups(query);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getTotal());
            assertEquals(1, result.getItems().size());
            BackupVO vo = result.getItems().get(0);
            assertEquals(BACKUP_ID, vo.getId());
            assertEquals("admin", vo.getCreatedByName());
            verify(backupMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should leave createdByName unset when user is unknown")
        void shouldLeaveCreatedByNameUnsetWhenUserUnknown() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();

            Page<Backup> mockPage = new Page<>(1, 20);
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setCreatedBy("unknown-user");
            mockPage.setRecords(List.of(backup));
            mockPage.setTotal(1);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);
            when(userLookupPort.findUsernamesByIds(List.of("unknown-user")))
                    .thenReturn(Collections.emptyMap());

            // Act
            var result = projection.listBackups(query);

            // Assert
            assertEquals(1, result.getItems().size());
            assertNull(result.getItems().get(0).getCreatedByName(),
                    "missing user must not populate createdByName");
        }

        @Test
        @DisplayName("should filter by type when query carries a type")
        void shouldFilterByType() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();
            query.setType(BackupType.FULL);

            Page<Backup> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            projection.listBackups(query);

            // Assert
            verify(backupMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should filter by status when query carries a status")
        void shouldFilterByStatus() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();
            query.setStatus(BackupStatus.COMPLETED);

            Page<Backup> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            projection.listBackups(query);

            // Assert
            verify(backupMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should call user port with empty id list on empty page (no enrichment needed)")
        void shouldCallUserPortWithEmptyIdsOnEmptyPage() {
            // Arrange
            BackupQueryDTO query = new BackupQueryDTO();

            Page<Backup> mockPage = new Page<>(1, 20);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);

            when(backupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // Act
            var result = projection.listBackups(query);

            // Assert
            verify(userLookupPort).findUsernamesByIds(Collections.emptyList());
            assertTrue(result.getItems().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    @Nested
    @DisplayName("getById Tests")
    class GetByIdTests {

        @Test
        @DisplayName("should return enriched backup when found")
        void shouldReturnEnrichedBackupWhenFound() {
            // Arrange
            Backup backup = new Backup();
            backup.setId(BACKUP_ID);
            backup.setFilename("backup_full_20240101_120000.sql");
            backup.setType(BackupType.FULL);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCreatedBy(USER_ID);

            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(userLookupPort.findUsernamesByIds(List.of(USER_ID)))
                    .thenReturn(Map.of(USER_ID, "admin"));

            // Act
            BackupVO result = projection.getById(BACKUP_ID);

            // Assert
            assertNotNull(result);
            assertEquals(BACKUP_ID, result.getId());
            assertEquals("admin", result.getCreatedByName());
        }

        @Test
        @DisplayName("should throw BusinessException when backup not found")
        void shouldThrowBusinessExceptionWhenBackupNotFound() {
            // Arrange
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> projection.getById(BACKUP_ID));
            assertEquals("Backup not found", exception.getMessage());
            verifyNoInteractions(userLookupPort);
        }
    }

    @Nested
    @DisplayName("toVO Tests")
    class ToVOTests {

        @Test
        @DisplayName("should convert Backup to BackupVO faithfully")
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
            BackupVO result = projection.toVO(backup);

            // Assert
            assertEquals(BACKUP_ID, result.getId());
            assertEquals("backup_full_20240101_120000.sql", result.getFilename());
            assertEquals(1024L, result.getSize());
            assertEquals(BackupType.FULL, result.getType());
            assertEquals(BackupStatus.COMPLETED, result.getStatus());
            assertEquals(USER_ID, result.getCreatedBy());
        }

        @Test
        @DisplayName("should return null when entity is null")
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(projection.toVO(null));
        }

        @Test
        @DisplayName("toVOList should return empty list on empty input without hitting the user port")
        void shouldReturnEmptyListOnEmptyInput() {
            // Act
            List<BackupVO> result = projection.toVOList(Collections.emptyList());

            // Assert
            assertTrue(result.isEmpty(),
                    "empty input must short-circuit to empty output");
            verifyNoInteractions(userLookupPort);
        }

        @Test
        @DisplayName("toVOList should return empty list when input is null")
        void shouldReturnEmptyListOnNullInput() {
            assertTrue(projection.toVOList(null).isEmpty());
        }

        @Test
        @DisplayName("toVOList should enrich every entity via a single user-port call")
        void shouldEnrichEveryEntityViaSinglePortCall() {
            // Arrange
            Backup a = new Backup();
            a.setId("a");
            a.setCreatedBy("u1");
            Backup b = new Backup();
            b.setId("b");
            b.setCreatedBy("u2");

            when(userLookupPort.findUsernamesByIds(List.of("u1", "u2")))
                    .thenReturn(Map.of("u1", "alice", "u2", "bob"));

            // Act
            List<BackupVO> result = projection.toVOList(List.of(a, b));

            // Assert
            assertEquals(2, result.size());
            assertEquals("alice", result.get(0).getCreatedByName());
            assertEquals("bob", result.get(1).getCreatedByName());
        }

        @Test
        @DisplayName("toVOList should call the port exactly once per list (deduped)")
        void shouldCallPortExactlyOnce() {
            // Arrange
            Backup a = new Backup();
            a.setId("a");
            a.setCreatedBy("u1");
            Backup b = new Backup();
            b.setId("b");
            b.setCreatedBy("u1");

            when(userLookupPort.findUsernamesByIds(List.of("u1")))
                    .thenReturn(Map.of("u1", "alice"));

            // Act
            projection.toVOList(List.of(a, b));

            // Assert — u1 deduped before reaching the port
            verify(userLookupPort, times(1)).findUsernamesByIds(List.of("u1"));
        }
    }
}
