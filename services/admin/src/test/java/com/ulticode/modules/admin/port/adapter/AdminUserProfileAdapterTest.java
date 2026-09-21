package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.uuid.UuidGenerator;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserProfileAdapterTest {

    @Mock
    private UuidGenerator uuidGenerator;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private FileStoragePort fileStorage;
    @Mock
    private ProfileWriteService profileWriteService;
    @Mock
    private com.ulticode.modules.admin.storage.AdminStorageCleanup adminStorageCleanup;
    private AdminUserProfileAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AdminUserProfileAdapter(uuidGenerator, currentUserProvider, fileStorage,
                adminStorageCleanup);
        ReflectionTestUtils.setField(adapter, "profileWriteService", profileWriteService);
    }

    @Test
    void queuesStagedObjectWhenTheCleanupDeleteFails() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        when(profileWriteService.uploadAvatar(any()))
                .thenThrow(new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "no provider"));
        doThrow(new RuntimeException("storage unavailable"))
                .when(fileStorage).delete("app/avatars/user-1/uuid-1.png");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Profile write RPC failed");

        verify(adminStorageCleanup).enqueue("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void storesObjectKeyAndPushesKeyThroughProfileWriteService() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        when(profileWriteService.uploadAvatar(any())).thenReturn(
                RpcResult.success(new ProfileWriteResult("user-1", null, null, null, null,
                        null, null, null, null, null), "trace-1"));
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "text/plain", png);

        String displayUrl = adapter.uploadAvatar("user-1", file);

        assertThat(displayUrl).isEqualTo("/api/users/avatars/user-1/uuid-1.png");
        verify(fileStorage).put(any(String.class), any(), anyLong(), org.mockito.ArgumentMatchers.eq("image/png"));
        ArgumentCaptor<UploadAvatarCommand> command = ArgumentCaptor.forClass(UploadAvatarCommand.class);
        verify(profileWriteService).uploadAvatar(command.capture());
        assertThat(command.getValue().avatarUrl()).isEqualTo("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void rejectsContentThatOnlyClaimsToBeAnImage() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1", file))
                .hasMessageContaining("supported image");
    }

    @Test
    void removesObjectWhenProfileWriteFails() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        when(profileWriteService.uploadAvatar(any()))
                .thenReturn(RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, "trace-1"));

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Unexpected app state");

        verify(fileStorage).delete("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void keepsObjectWhenProfileWriteOutcomeIsUnknown() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        when(profileWriteService.uploadAvatar(any())).thenThrow(new RuntimeException("provider timeout"));

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Profile write RPC failed");

        verify(fileStorage, org.mockito.Mockito.never()).delete(any());
        // App may have committed the key, so the object stays but a durable
        // owner-checked intent decides its fate instead of leaving it orphaned.
        verify(adminStorageCleanup).enqueueForOwnerCheck("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void removesObjectWhenProfileProviderIsUnavailableBeforeDispatch() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        when(profileWriteService.uploadAvatar(any()))
                .thenThrow(new RpcException(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "no provider"));

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Profile write RPC failed");

        verify(fileStorage).delete("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void removesObjectWhenDirectoryIsForbiddenBeforeDispatch() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        when(profileWriteService.uploadAvatar(any()))
                .thenThrow(new RpcException(RpcException.FORBIDDEN_EXCEPTION,
                        "No provider available from registry"));

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Profile write RPC failed");

        verify(fileStorage).delete("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void removesObjectWhenProfileServiceReferenceIsAbsent() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ReflectionTestUtils.setField(adapter, "profileWriteService", null);

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("ProfileWriteService unavailable");

        verify(fileStorage).delete("app/avatars/user-1/uuid-1.png");
    }

    @Test
    void removesObjectWhenProfileWriteTransactionRollsBack() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(uuidGenerator.newId()).thenReturn("uuid-1");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        when(profileWriteService.uploadAvatar(any()))
                .thenThrow(new UnexpectedRollbackException("transaction marked rollback-only"));

        assertThatThrownBy(() -> adapter.uploadAvatar("user-1",
                new MockMultipartFile("file", "photo.png", "image/png", png)))
                .hasMessageContaining("Profile write RPC failed");

        verify(fileStorage).delete("app/avatars/user-1/uuid-1.png");
    }
}
