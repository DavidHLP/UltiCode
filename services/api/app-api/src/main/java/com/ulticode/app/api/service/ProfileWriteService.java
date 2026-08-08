package com.ulticode.app.api.service;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned profile write provider.
 *
 * <p>Establishes {@code backend-app} as the sole write owner of the
 * {@code user_profiles} table. Consumers (initially the legacy user
 * controller during migration; later a relocated App endpoint) issue
 * {@link UpdateProfileCommand} to mutate profile fields.
 *
 * <p>Per migration guide §4.3, profile data is App-owned. This interface
 * is contract-only; the provider implementation belongs to
 * {@code backend-app}.
 */
public interface ProfileWriteService {

    /**
     * Update profile fields for the given account.
     *
     * <p>Only non-null fields in the command are applied; null fields
     * are left unchanged. The provider writes exclusively to the
     * {@code user_profiles} table and never touches the Auth-owned
     * {@code users} table.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the account id and
     *                the profile fields to update
     * @return success with the post-update {@link ProfileWriteResult};
     *         failure codes as documented on {@code AppErrorCode}
     */
    RpcResult<ProfileWriteResult> updateProfile(UpdateProfileCommand command);

    /**
     * Set the avatar URL for the given account.
     *
     * <p>This is a dedicated avatar-upload operation, distinct from
     * {@link #updateProfile(UpdateProfileCommand)}: the HTTP layer
     * handles file upload (multipart, storage), then issues this
     * command with the resulting storage URL. Writes exclusively to
     * the {@code user_profiles.avatar} column.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, account id and avatar URL
     * @return success with the post-update {@link ProfileWriteResult};
     *         failure codes as documented on {@code AppErrorCode}
     */
    RpcResult<ProfileWriteResult> uploadAvatar(UploadAvatarCommand command);
}
