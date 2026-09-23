package com.ulticode.app.userprofile;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.port.DefaultAppUserWritePort;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;

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

    private ProfileMutationModule profileMutationModule;
    private CommandReceiptExecutor receiptExecutor;
    private AdminActorAuthorizer actorAuthorizer;
    private ProfileWriteProvider provider;

    @BeforeEach
    void setUp() {
        profileMutationModule = mock(ProfileMutationModule.class);
        receiptExecutor = mock(CommandReceiptExecutor.class);
        actorAuthorizer = mock(AdminActorAuthorizer.class);
        provider = new ProfileWriteProvider(profileMutationModule, receiptExecutor, actorAuthorizer);
    }

    @Test
    void updateAdaptersDeclareSameProfileWriteCacheEvictions() throws NoSuchMethodException {
        CacheEvict rpc = ProfileWriteProvider.class
                .getMethod("updateProfile", UpdateProfileCommand.class)
                .getAnnotation(CacheEvict.class);
        CacheEvict http = DefaultAppUserWritePort.class
                .getMethod("updateProfile", String.class, UpdateUserDTO.class)
                .getAnnotation(CacheEvict.class);

        assertThat(rpc.value()).containsExactlyInAnyOrder(
                ProfileMutationModule.USER_STATS_CACHE, ProfileMutationModule.CONTEST_RANKING_CACHE);
        assertThat(http.value()).containsExactlyInAnyOrder(rpc.value());
    }

    @Test
    void rejectsUntrustedProfileUpdateBeforeReceiptAndMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);

        RpcResult<?> result = provider.updateProfile(updateCommand("user-1", "Name", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(receiptExecutor, profileMutationModule);
    }

    @Test
    void rejectsUntrustedAvatarUpdateBeforeReceiptAndMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(false);

        RpcResult<?> result = provider.uploadAvatar(avatarCommand("user-1", "app/avatars/user-1/new.png"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verifyNoInteractions(receiptExecutor, profileMutationModule);
    }

    @Test
    void updateDelegatesProfilePatchThroughReceiptBoundary() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-1", "Alice", "Engineer");
        ProfileWriteResult expected = new ProfileWriteResult(
                "user-1", "Alice", null, "Engineer", null, null, null, null, null, null);
        when(profileMutationModule.update(any(ProfilePatch.class))).thenReturn(expected);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenAnswer(invocation -> invokeMutation(invocation, "trace"));

        RpcResult<ProfileWriteResult> result = provider.updateProfile(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        ArgumentCaptor<ProfilePatch> patch = ArgumentCaptor.forClass(ProfilePatch.class);
        verify(profileMutationModule).update(patch.capture());
        assertThat(patch.getValue().accountId()).isEqualTo("user-1");
        assertThat(patch.getValue().name()).isEqualTo("Alice");
        assertThat(patch.getValue().bio()).isEqualTo("Engineer");
        verify(receiptExecutor).execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any());
    }

    @Test
    void uploadDelegatesAvatarReferenceThroughReceiptBoundary() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UploadAvatarCommand command = avatarCommand("user-2", "app/avatars/user-2/new.png");
        ProfileWriteResult expected = new ProfileWriteResult(
                "user-2", null, "app/avatars/user-2/new.png", null, null,
                null, null, null, null, null);
        when(profileMutationModule.replaceAvatar("user-2", "app/avatars/user-2/new.png"))
                .thenReturn(expected);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("uploadAvatar"), eq(command),
                eq(ProfileWriteResult.class), any())).thenAnswer(invocation -> invokeMutation(invocation, "trace"));

        RpcResult<ProfileWriteResult> result = provider.uploadAvatar(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        verify(profileMutationModule).replaceAvatar("user-2", "app/avatars/user-2/new.png");
    }

    @Test
    void mapsReceiptReplayResultWithoutRunningMutation() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-3", "Alice", null);
        ProfileWriteResult expected = new ProfileWriteResult(
                "user-3", "Alice", null, null, null, null, null, null, null, null);
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenReturn(RpcResult.success(expected, "trace"));

        RpcResult<ProfileWriteResult> result = provider.updateProfile(command);

        assertThat(result.data()).isEqualTo(expected);
        verify(profileMutationModule, never()).update(any(ProfilePatch.class));
    }

    @Test
    void mapsMutationBusinessFailureAtRpcBoundary() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand(
                "user-4", null, "app/avatars/user-4/displaced.png");
        when(profileMutationModule.update(any(ProfilePatch.class)))
                .thenThrow(new BusinessException(BaseErrorCode.BAD_REQUEST, "avatar upload endpoint"));
        when(receiptExecutor.execute(
                eq("ProfileWriteService"), eq("updateProfile"), eq(command),
                eq(ProfileWriteResult.class), any())).thenAnswer(invocation -> invokeMutation(invocation, "trace"));

        RpcResult<?> result = provider.updateProfile(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.BAD_REQUEST.code());
    }

    @Test
    void mapsUnexpectedMutationFailureAtRpcBoundary() {
        when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        UpdateProfileCommand command = updateCommand("user-5", "Alice", null);
        when(profileMutationModule.update(any(ProfilePatch.class)))
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

    private static UpdateProfileCommand updateCommand(String accountId, String name, String bio) {
        return new UpdateProfileCommand(
                "profile-command-" + accountId,
                IdMetadata.mint(),
                adminActor(),
                new TraceMetadata("trace-update", null, null, null),
                accountId, name, null, bio,
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
