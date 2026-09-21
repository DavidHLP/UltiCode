package com.ulticode.modules.backup.service;

import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import com.ulticode.modules.backup.service.impl.BackupObjectCleanup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the durable, retrying backup-object deletion path. */
@ExtendWith(MockitoExtension.class)
class BackupObjectCleanupTest {

    private static final String OBJECT_KEY = "admin/backups/2026/01/test-backup-id.sql";
    private static final String OTHER_OBJECT_KEY = "admin/backups/2026/01/other-backup-id.sql";

    @Mock
    private BackupDeletionTombstoneMapper backupDeletionTombstoneMapper;

    @Mock
    private FileStoragePort fileStorage;

    @InjectMocks
    private BackupObjectCleanup backupObjectCleanup;

    @Test
    @DisplayName("a successful delete clears the pending tombstone")
    void shouldClearTombstoneAfterDelete() {
        when(backupDeletionTombstoneMapper.markObjectDeleted(OBJECT_KEY)).thenReturn(1);

        backupObjectCleanup.deletePending(OBJECT_KEY);

        verify(fileStorage).delete(OBJECT_KEY);
        verify(backupDeletionTombstoneMapper).markObjectDeleted(OBJECT_KEY);
        verify(backupDeletionTombstoneMapper, never()).recordObjectDeleteFailure(anyString(), anyString());
    }

    @Test
    @DisplayName("a storage failure leaves the object pending with the recorded error")
    void shouldKeepObjectPendingWhenDeleteFails() {
        doThrow(new RuntimeException("rustfs unavailable")).when(fileStorage).delete(OBJECT_KEY);

        backupObjectCleanup.deletePending(OBJECT_KEY);

        verify(backupDeletionTombstoneMapper).recordObjectDeleteFailure(OBJECT_KEY, "rustfs unavailable");
        verify(backupDeletionTombstoneMapper, never()).markObjectDeleted(anyString());
    }

    @Test
    @DisplayName("the sweep retries every pending object even when one delete fails")
    void shouldRetryEveryPendingObject() {
        when(backupDeletionTombstoneMapper.selectPendingObjectKeys(anyInt(), anyInt()))
                .thenReturn(List.of(OBJECT_KEY, OTHER_OBJECT_KEY));
        doThrow(new RuntimeException("rustfs unavailable")).when(fileStorage).delete(OBJECT_KEY);

        assertEquals(2, backupObjectCleanup.sweep());

        verify(fileStorage).delete(OBJECT_KEY);
        verify(fileStorage).delete(OTHER_OBJECT_KEY);
        verify(backupDeletionTombstoneMapper, times(1)).markObjectDeleted(OTHER_OBJECT_KEY);
        verify(backupDeletionTombstoneMapper).recordObjectDeleteFailure(OBJECT_KEY, "rustfs unavailable");
    }

    @Test
    @DisplayName("an unreadable tombstone table ends the sweep without deleting objects")
    void shouldStopSweepWhenPendingReadFails() {
        when(backupDeletionTombstoneMapper.selectPendingObjectKeys(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("database unavailable"));

        assertEquals(0, backupObjectCleanup.sweep());

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("a cleared tombstone that the database cannot update reports no pending row")
    void shouldTolerateMissingPendingRowAfterDelete() {
        when(backupDeletionTombstoneMapper.markObjectDeleted(OBJECT_KEY)).thenReturn(0);

        backupObjectCleanup.deletePending(OBJECT_KEY);

        verify(fileStorage).delete(OBJECT_KEY);
        verify(backupDeletionTombstoneMapper, never()).recordObjectDeleteFailure(anyString(), anyString());
    }

    @Test
    @DisplayName("the recorded failure message is bounded to the column width")
    void shouldBoundRecordedError() {
        doThrow(new RuntimeException("x".repeat(900))).when(fileStorage).delete(OBJECT_KEY);

        backupObjectCleanup.deletePending(OBJECT_KEY);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(backupDeletionTombstoneMapper).recordObjectDeleteFailure(eq(OBJECT_KEY), error.capture());
        assertEquals(500, error.getValue().length());
    }
}
