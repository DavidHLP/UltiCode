package com.ulticode.app.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.port.UserDirectoryRow;
import com.ulticode.modules.search.port.UserSearchRow;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProfileWriteProviderTest {

    private UserProfileMapper userProfileMapper;
    private AppCommandReceiptMapper receiptMapper;
    private ObjectMapper objectMapper;
    private AdminActorAuthorizer actorAuthorizer;
    private StorageCleanupOutbox storageCleanupOutbox;
    private UserDirectoryQueryPort userDirectoryQueryPort;
    private SearchDocumentChangedPublisher searchPublisher;
    private ObjectProvider<UserDirectoryQueryPort> userDirectoryQueryPortProvider;
    private ObjectProvider<SearchDocumentChangedPublisher> searchPublisherProvider;
    private ProfileWriteProvider provider;

    @BeforeEach
    void setUp() {
        userProfileMapper = mock(UserProfileMapper.class);
        receiptMapper = mock(AppCommandReceiptMapper.class);
        objectMapper = mock(ObjectMapper.class);
        actorAuthorizer = mock(AdminActorAuthorizer.class);
        storageCleanupOutbox = mock(StorageCleanupOutbox.class);
        userDirectoryQueryPort = mock(UserDirectoryQueryPort.class);
        searchPublisher = mock(SearchDocumentChangedPublisher.class);
        userDirectoryQueryPortProvider = mock(ObjectProvider.class);
        searchPublisherProvider = mock(ObjectProvider.class);
        when(userDirectoryQueryPortProvider.getIfAvailable()).thenReturn(userDirectoryQueryPort);
        when(searchPublisherProvider.getIfAvailable()).thenReturn(searchPublisher);
        provider = new ProfileWriteProvider(userProfileMapper, receiptMapper, objectMapper, actorAuthorizer,
                storageCleanupOutbox, userDirectoryQueryPortProvider, searchPublisherProvider);
    }

    @Test
    void rejectsUntrustedProfileUpdateBeforeDatabaseMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);

        RpcResult<?> result = provider.updateProfile(new UpdateProfileCommand(
                "profile-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-1", "Name", null, null, null, null, null, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(userProfileMapper, receiptMapper);
    }

    @Test
    void rejectsUntrustedAvatarUpdateBeforeDatabaseMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-1", "/uploads/avatars/avatar.png"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(userProfileMapper, receiptMapper);
    }

    @Test
    void profileUpdatePublishesCompleteUserUpsert() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        when(userProfileMapper.selectByIdForUpdate("user-1")).thenReturn(new UserProfile());
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-1"))
                .thenReturn(directoryRow("user-1", "alice", "New Name", "app/avatars/user-1/avatar.png"));

        RpcResult<?> result = provider.updateProfile(new UpdateProfileCommand(
                "profile-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-1", "New Name", null, null, null, null, null, null, null, null));

        assertThat(result.success()).isTrue();
        verify(searchPublisher).publishUser(
                "user-1", "alice", "New Name", "app/avatars/user-1/avatar.png", true);
    }

    @Test
    void avatarUpdatePublishesCompleteUserUpsert() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-9");
        when(userProfileMapper.selectByIdForUpdate("user-9")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-9"))
                .thenReturn(directoryRow("user-9", "alice", "Alice", "app/avatars/user-9/new.png"));

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-9", "app/avatars/user-9/new.png"));

        assertThat(result.success()).isTrue();
        verify(searchPublisher).publishUser(
                "user-9", "alice", "Alice", "app/avatars/user-9/new.png", true);
    }

    @Test
    void avatarUpdateSearchPublishFailureReturnsFailureAndRequiresRollback() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-10");
        when(userProfileMapper.selectByIdForUpdate("user-10")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(userDirectoryQueryPort.findById("user-10"))
                .thenReturn(directoryRow("user-10", "alice", "Alice", "app/avatars/user-10/new.png"));
        doThrow(new IllegalStateException("search unavailable"))
                .when(searchPublisher).publishUser(any(), any(), any(), any(), anyBoolean());

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-10", "app/avatars/user-10/new.png"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
        verify(searchPublisher).publishUser(
                "user-10", "alice", "Alice", "app/avatars/user-10/new.png", true);
    }

    @Test
    void replacedAvatarQueuesDurableCleanupIntent() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-9");
        existing.setAvatar("app/avatars/user-9/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-9")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-9", "app/avatars/user-9/new.png"));

        assertThat(result.success()).isTrue();
        verify(storageCleanupOutbox).enqueue("app/avatars/user-9/old.png");
    }

    @Test
    void cleanupQueueFailureFailsTheRpc() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-9");
        existing.setAvatar("app/avatars/user-9/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-9")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        doThrow(new RuntimeException("cleanup outbox unavailable"))
                .when(storageCleanupOutbox).enqueue("app/avatars/user-9/old.png");

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-9", "app/avatars/user-9/new.png"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
    }

    @Test
    void zeroRowAvatarUpdateFailsAndQueuesNoCleanup() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-9");
        existing.setAvatar("app/avatars/user-9/old.png");
        when(userProfileMapper.selectByIdForUpdate("user-9")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(0);

        RpcResult<?> result = provider.uploadAvatar(new UploadAvatarCommand(
                "avatar-command", IdMetadata.mint(), adminActor(), TraceMetadata.EMPTY,
                "user-9", "app/avatars/user-9/new.png"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
        // The stale object must survive a no-op write: the row still references it.
        verify(storageCleanupOutbox, never()).enqueue(any());
    }

    private static UserDirectoryRow directoryRow(
            String id, String username, String name, String avatar) {
        UserSearchRow row = new UserSearchRow();
        row.setId(id);
        row.setUsername(username);
        row.setName(name);
        row.setAvatar(avatar);
        return UserDirectoryRow.from(row);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }
}
