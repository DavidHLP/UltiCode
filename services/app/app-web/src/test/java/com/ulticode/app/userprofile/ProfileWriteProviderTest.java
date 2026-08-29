package com.ulticode.app.userprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.app.userprofile.provider.ProfileWriteProvider;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileWriteProviderTest {

    private UserProfileMapper userProfileMapper;
    private AppCommandReceiptMapper receiptMapper;
    private ObjectMapper objectMapper;
    private AdminActorAuthorizer actorAuthorizer;
    private ProfileWriteProvider provider;

    @BeforeEach
    void setUp() {
        userProfileMapper = mock(UserProfileMapper.class);
        receiptMapper = mock(AppCommandReceiptMapper.class);
        objectMapper = mock(ObjectMapper.class);
        actorAuthorizer = mock(AdminActorAuthorizer.class);
        provider = new ProfileWriteProvider(userProfileMapper, receiptMapper, objectMapper, actorAuthorizer);
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

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }
}
