package com.ulticode.modules.admin.storage;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.storage.FileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminStorageCleanupTest {

    private static final String ACCOUNT_ID = "user-1";
    private static final String OBJECT_KEY = "app/avatars/user-1/uuid-1.png";
    private static final String OTHER_OBJECT_KEY = "app/avatars/user-2/uuid-2.png";
    private static final String DISPLAY_PATH = "/api/users/avatars/user-1/uuid-1.png";

    @Mock
    private AdminStorageCleanupOutboxMapper outboxMapper;

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private ObjectProvider<UserProfileQueryService> profileQueryServiceProvider;

    @Mock
    private UserProfileQueryService profileQueryService;

    @InjectMocks
    private AdminStorageCleanup adminStorageCleanup;

    @BeforeEach
    void setUp() {
        when(profileQueryServiceProvider.getIfAvailable()).thenReturn(profileQueryService);
    }

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
        when(outboxMapper.selectPendingDeletions(anyInt()))
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
        when(outboxMapper.selectPendingDeletions(anyInt()))
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

    @Test
    @DisplayName("an object App still references is kept, not deleted")
    void shouldKeepObjectAppStillReferences() {
        when(outboxMapper.selectPendingOwnerChecks(anyInt())).thenReturn(List.of(OBJECT_KEY));
        when(profileQueryService.getProfileByAccountId(ACCOUNT_ID))
                .thenReturn(RpcResult.success(profile(DISPLAY_PATH), "trace-1"));

        assertEquals(1, adminStorageCleanup.sweep());

        verify(fileStorage, never()).delete(anyString());
        verify(outboxMapper).markKept(OBJECT_KEY);
    }

    @Test
    @DisplayName("an object App no longer references is deleted")
    void shouldDeleteObjectAppNoLongerReferences() {
        when(outboxMapper.selectPendingOwnerChecks(anyInt())).thenReturn(List.of(OBJECT_KEY));
        when(profileQueryService.getProfileByAccountId(ACCOUNT_ID))
                .thenReturn(RpcResult.success(profile("/api/users/avatars/user-1/other.png"), "trace-1"));
        when(outboxMapper.markDeleted(OBJECT_KEY)).thenReturn(1);

        adminStorageCleanup.sweep();

        verify(fileStorage).delete(OBJECT_KEY);
        verify(outboxMapper).markDeleted(OBJECT_KEY);
        verify(outboxMapper, never()).markKept(anyString());
    }

    @Test
    @DisplayName("a profile that vanished means the object is unreferenced")
    void shouldDeleteObjectWhenProfileIsAbsent() {
        when(outboxMapper.selectPendingOwnerChecks(anyInt())).thenReturn(List.of(OBJECT_KEY));
        when(profileQueryService.getProfileByAccountId(ACCOUNT_ID))
                .thenReturn(RpcResult.success((UserProfileDTO) null, "trace-1"));
        when(outboxMapper.markDeleted(OBJECT_KEY)).thenReturn(1);

        adminStorageCleanup.sweep();

        verify(fileStorage).delete(OBJECT_KEY);
    }

    @Test
    @DisplayName("an unavailable App leaves the owner check pending")
    void shouldStayPendingWhileAppIsUnavailable() {
        when(outboxMapper.selectPendingOwnerChecks(anyInt())).thenReturn(List.of(OBJECT_KEY));
        when(profileQueryService.getProfileByAccountId(ACCOUNT_ID))
                .thenThrow(new RuntimeException("app unavailable"));

        adminStorageCleanup.sweep();

        verify(fileStorage, never()).delete(anyString());
        verify(outboxMapper, never()).markKept(anyString());
        verify(outboxMapper).recordFailure(OBJECT_KEY, "app unavailable");
    }

    @Test
    @DisplayName("the mapper package is inside the bounded Admin mapper scan")
    void mapperPackageIsScanned() {
        // There is no auto-configuration fallback: an unscanned mapper package
        // leaves AdminStorageCleanup without a mapper bean and the whole
        // context fails to start.
        org.mybatis.spring.annotation.MapperScan scan =
                com.ulticode.BackendAdminApplication.class
                        .getAnnotation(org.mybatis.spring.annotation.MapperScan.class);

        assertThat(java.util.Arrays.asList(scan.value()))
                .contains(AdminStorageCleanupOutboxMapper.class.getPackageName());
    }

    private static UserProfileDTO profile(String avatar) {
        return new UserProfileDTO(ACCOUNT_ID, "Alice", avatar, null, null, null, null, null, null, null);
    }
}
