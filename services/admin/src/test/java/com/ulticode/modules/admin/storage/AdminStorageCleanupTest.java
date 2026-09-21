package com.ulticode.modules.admin.storage;

import com.ulticode.common.storage.FileStoragePort;
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

/** Unit tests for durable, retrying cleanup of staged Admin uploads. */
@ExtendWith(MockitoExtension.class)
class AdminStorageCleanupTest {

    private static final String OBJECT_KEY = "app/avatars/user-1/uuid-1.png";
    private static final String OTHER_OBJECT_KEY = "app/avatars/user-2/uuid-2.png";

    @Mock
    private AdminStorageCleanupOutboxMapper outboxMapper;

    @Mock
    private FileStoragePort fileStorage;

    @InjectMocks
    private AdminStorageCleanup adminStorageCleanup;

    @Test
    @DisplayName("a successful delete clears the pending intent")
    void shouldClearIntentAfterDelete() {
        when(outboxMapper.markDeleted(OBJECT_KEY)).thenReturn(1);

        adminStorageCleanup.deletePending(OBJECT_KEY);

        verify(fileStorage).delete(OBJECT_KEY);
        verify(outboxMapper).markDeleted(OBJECT_KEY);
        verify(outboxMapper, never()).recordFailure(anyString(), anyString());
    }

    @Test
    @DisplayName("a storage failure leaves the key pending with the recorded error")
    void shouldKeepKeyPendingWhenDeleteFails() {
        doThrow(new RuntimeException("rustfs unavailable")).when(fileStorage).delete(OBJECT_KEY);

        adminStorageCleanup.deletePending(OBJECT_KEY);

        verify(outboxMapper).recordFailure(OBJECT_KEY, "rustfs unavailable");
        verify(outboxMapper, never()).markDeleted(anyString());
    }

    @Test
    @DisplayName("the sweep retries every pending key even when one delete fails")
    void shouldRetryEveryPendingKey() {
        when(outboxMapper.selectPendingObjectKeys(anyInt()))
                .thenReturn(List.of(OBJECT_KEY, OTHER_OBJECT_KEY));
        doThrow(new RuntimeException("rustfs unavailable")).when(fileStorage).delete(OBJECT_KEY);

        assertEquals(2, adminStorageCleanup.sweep());

        verify(fileStorage).delete(OBJECT_KEY);
        verify(fileStorage).delete(OTHER_OBJECT_KEY);
        verify(outboxMapper, times(1)).markDeleted(OTHER_OBJECT_KEY);
        verify(outboxMapper).recordFailure(OBJECT_KEY, "rustfs unavailable");
    }

    @Test
    @DisplayName("an unreadable intent table ends the sweep without deleting objects")
    void shouldStopSweepWhenPendingReadFails() {
        when(outboxMapper.selectPendingObjectKeys(anyInt()))
                .thenThrow(new RuntimeException("database unavailable"));

        assertEquals(0, adminStorageCleanup.sweep());

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("the recorded failure message is bounded to the column width")
    void shouldBoundRecordedError() {
        doThrow(new RuntimeException("x".repeat(900))).when(fileStorage).delete(OBJECT_KEY);

        adminStorageCleanup.deletePending(OBJECT_KEY);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).recordFailure(eq(OBJECT_KEY), error.capture());
        assertEquals(500, error.getValue().length());
    }
}
