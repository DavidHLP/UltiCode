package com.ulticode.modules.backup.service;

import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.service.impl.BackupExecutionServiceImpl;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the async backup execution lifecycle.
 *
 * <p>This suite is the test surface that proves the deep lifecycle seam:
 * it exercises {@link BackupExecutionService#executeBackup} directly, with
 * an in-memory {@link BackupProcessPort}, against the real
 * {@link BackupExecutionServiceImpl}. It pins three properties that the
 * old in-class {@code @Async} self-invocation could not be tested for:
 *
 * <ol>
 *   <li>the lifecycle transitions {@code PENDING &rarr; IN_PROGRESS &rarr;
 *       COMPLETED} on success and {@code &rarr; FAILED} on dump failure,
 *       missing file, or exception;</li>
 *   <li>every run records a terminal state and a non-null
 *       {@code completedAt};</li>
 *   <li>every failure path captures an error string before persisting.</li>
 * </ol>
 *
 * <p>The dispatch-separation contract &mdash; {@code createBackup} must
 * dispatch through the injected {@link BackupExecutionService} bean, not via
 * {@code this.executeBackup()} &mdash; is asserted in
 * {@code BackupServiceTest.shouldDispatchViaBackupExecutionService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupExecutionServiceTest {

    private static final String BACKUP_ID = "test-backup-id";

    @Mock
    private BackupMapper backupMapper;

    @Mock
    private Clock clock;

    @Mock
    private BackupProcessPort backupProcessPort;

    @InjectMocks
    private BackupExecutionServiceImpl executionService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(executionService, "backupDir", tempDir.toString());
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
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

    @Nested
    @DisplayName("lifecycle transitions")
    class LifecycleTransitions {

        @Test
        @DisplayName("PENDING -> IN_PROGRESS -> COMPLETED when dump succeeds and file exists")
        void shouldCompleteWhenDumpSucceeds() throws Exception {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            Path expectedFile = tempDir.resolve("backup_full_test.sql");
            when(backupProcessPort.dump(eq(expectedFile))).thenAnswer(inv -> {
                Files.writeString(expectedFile, "-- fake dump");
                return true;
            });

            executionService.executeBackup(BACKUP_ID);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, atLeast(2)).updateById(captor.capture());
            Backup finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);

            assertEquals(BackupStatus.COMPLETED, finalState.getStatus(),
                    "backup must reach COMPLETED when dump succeeds");
            assertNotNull(finalState.getCompletedAt(), "completedAt must be recorded");
            assertTrue(finalState.getSize() > 0, "size must be set from the dumped file");
            assertNotNull(finalState.getMetadata(), "completion metadata must be recorded");
            assertEquals("FULL", finalState.getMetadata().get("backupType"));
        }

        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump reports failure")
        void shouldFailWhenDumpReportsFailure() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any())).thenReturn(false);

            executionService.executeBackup(BACKUP_ID);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, atLeast(2)).updateById(captor.capture());
            Backup finalState = captor.getValue();

            assertEquals(BackupStatus.FAILED, finalState.getStatus(),
                    "backup must reach FAILED when dump reports failure");
            assertNotNull(finalState.getCompletedAt());
            assertNotNull(finalState.getError(), "failure must capture an error message");
        }

        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump succeeds but file is missing")
        void shouldFailWhenFileMissingAfterDump() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            // Dump reports success but never creates the file
            when(backupProcessPort.dump(any())).thenReturn(true);

            executionService.executeBackup(BACKUP_ID);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, atLeast(2)).updateById(captor.capture());
            Backup finalState = captor.getValue();

            assertEquals(BackupStatus.FAILED, finalState.getStatus(),
                    "backup must reach FAILED when the file is missing after dump");
            assertNotNull(finalState.getError());
        }

        @Test
        @DisplayName("IN_PROGRESS -> FAILED when dump throws, error message captured")
        void shouldFailWhenDumpThrows() {
            Backup backup = pendingBackup();
            when(backupMapper.selectById(BACKUP_ID)).thenReturn(backup);
            when(backupProcessPort.dump(any())).thenThrow(new RuntimeException("mysqldump not on PATH"));

            executionService.executeBackup(BACKUP_ID);

            ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
            verify(backupMapper, atLeast(2)).updateById(captor.capture());
            Backup finalState = captor.getValue();

            assertEquals(BackupStatus.FAILED, finalState.getStatus());
            assertEquals("mysqldump not on PATH", finalState.getError(),
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

            executionService.executeBackup(BACKUP_ID);

            verify(backupMapper, never()).updateById(any(Backup.class));
            verifyNoInteractions(backupProcessPort);
        }
    }
}
