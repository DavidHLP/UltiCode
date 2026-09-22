package com.ulticode.app.userprofile;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileWriteProviderTest {

    private UserProfileMapper userProfileMapper;
    private CommandReceiptExecutor receiptExecutor;
    private AdminActorAuthorizer actorAuthorizer;
    private StorageCleanupOutbox storageCleanupOutbox;
    private ObjectProvider<UserDirectoryQueryPort> userDirectoryQueryPort;
    private ObjectProvider<SearchDocumentChangedPublisher> searchPublisher;
    private ProfileWriteProvider provider;

    @BeforeEach
    void setUp() {
        userProfileMapper = mock(UserProfileMapper.class);
        receiptExecutor = mock(CommandReceiptExecutor.class);
        actorAuthorizer = mock(AdminActorAuthorizer.class);
        storageCleanupOutbox = mock(StorageCleanupOutbox.class);
        userDirectoryQueryPort = mock(ObjectProvider.class);
        searchPublisher = mock(ObjectProvider.class);
        provider = new ProfileWriteProvider(
                userProfileMapper,
                receiptExecutor,
                actorAuthorizer,
                storageCleanupOutbox,
                userDirectoryQueryPort,
                searchPublisher);
    }

    @Test
    void rejectsUntrustedProfileUpdateBeforeReceiptAndMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);
        UpdateProfileCommand command = updateCommand("user-1", "Name", null);

        RpcResult<?> result = provider.updateProfile(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(receiptExecutor, userProfileMapper);
    }

    @Test
    void rejectsUntrustedAvatarUpdateBeforeReceiptAndMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);
        UploadAvatarCommand command = avatarCommand("user-1", "app/avatars/user-1/new.png");

        RpcResult<?> result = provider.uploadAvatar(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(receiptExecutor, userProfileMapper);
    }

    @Test
    void updateProfileUsesFixedReceiptContractAndMapsProfileFields() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-1", "Alice", "Engineer");
        when(userProfileMapper.selectByIdForUpdate("user-1")).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"),
                eq("updateProfile"),
                eq(command),
                eq(ProfileWriteResult.class),
                any())).thenAnswer(invocation -> invokeMutation(invocation, "trace-update"));

        RpcResult<ProfileWriteResult> result = provider.updateProfile(command);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).insert(profile.capture());
        assertThat(profile.getValue().getAccountId()).isEqualTo("user-1");
        assertThat(profile.getValue().getName()).isEqualTo("Alice");
        assertThat(profile.getValue().getBio()).isEqualTo("Engineer");
        verify(receiptExecutor).execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any());
    }

    @Test
    void uploadAvatarUsesFixedReceiptContractAndMapsAvatarInput() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UploadAvatarCommand command = avatarCommand("user-2", "app/avatars/user-2/new.png");
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-2");
        when(userProfileMapper.selectByIdForUpdate("user-2")).thenReturn(existing);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"),
                eq("uploadAvatar"),
                eq(command),
                eq(ProfileWriteResult.class),
                any())).thenAnswer(invocation -> invokeMutation(invocation, "trace-avatar"));

        RpcResult<ProfileWriteResult> result = provider.uploadAvatar(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().avatar()).isEqualTo("app/avatars/user-2/new.png");
        verify(receiptExecutor).execute(
                eq("ProfileWriteService"), eq("uploadAvatar"), eq(command),
                eq(ProfileWriteResult.class), any());
    }

    @Test
    void mapsMutationResultThroughReceiptExecutor() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-3", "Alice", null);
        ProfileWriteResult expected = new ProfileWriteResult(
                "user-3", "Alice", null, null, null, null, null, null, null, null);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenReturn(RpcResult.success(expected, "trace"));

        RpcResult<ProfileWriteResult> result = provider.updateProfile(command);

        assertThat(result.data()).isEqualTo(expected);
    }

    @Test
    void mapsDomainRejectionWithoutWritingProfile() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand(
                "user-4", null, "app/avatars/user-4/displaced.png");
        UserProfile existing = new UserProfile();
        existing.setAccountId("user-4");
        existing.setAvatar("app/avatars/user-4/current.png");
        when(userProfileMapper.selectByIdForUpdate("user-4")).thenReturn(existing);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenAnswer(invocation -> invokeMutation(invocation, "trace"));

        RpcResult<?> result = provider.updateProfile(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
        verify(userProfileMapper, never()).updateById(any(UserProfile.class));
        verify(userProfileMapper, never()).insert(any(UserProfile.class));
    }

    @Test
    void mapsUnexpectedMutationFailureAfterReceiptBoundary() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-5", "Alice", null);
        when(userProfileMapper.selectByIdForUpdate("user-5"))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenAnswer(invocation -> invokeMutation(invocation, "trace"));

        RpcResult<?> result = provider.updateProfile(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
    }

    private static RpcResult<ProfileWriteResult> invokeMutation(
            org.mockito.invocation.InvocationOnMock invocation, String traceId) {
        Function<String, RpcResult<ProfileWriteResult>> mutation = invocation.getArgument(4);
        return mutation.apply(traceId);
    }

    private static UpdateProfileCommand updateCommand(String accountId, String name, String avatar) {
        return new UpdateProfileCommand(
                "profile-command-" + accountId,
                IdMetadata.mint(),
                adminActor(),
                new TraceMetadata("trace-update", null, null, null),
                accountId, name, avatar, name == null ? null : "Engineer",
                null, null, null, null, null, null);
    }

    private static UploadAvatarCommand avatarCommand(String accountId, String avatarUrl) {
        return new UploadAvatarCommand(
                "avatar-command-" + accountId,
                IdMetadata.mint(),
                adminActor(),
                new TraceMetadata("trace-avatar", null, null, null),
                accountId, avatarUrl);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }
}
