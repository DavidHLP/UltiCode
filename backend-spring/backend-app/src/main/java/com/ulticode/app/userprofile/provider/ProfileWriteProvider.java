package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.TraceMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dubbo provider implementing {@link ProfileWriteService}.
 *
 * <p>Writes exclusively to the {@code user_profiles} table. Never reads
 * or writes the Auth-owned {@code users} table.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0", timeout = 5000, retries = 0)
@RequiredArgsConstructor
public class ProfileWriteProvider implements ProfileWriteService {

    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional
    public RpcResult<ProfileWriteResult> updateProfile(UpdateProfileCommand command) {
        String traceId = safeTraceId(command);
        try {
            String accountId = command.accountId();

            UserProfile profile = userProfileMapper.selectById(accountId);
            boolean isNew = profile == null;
            if (isNew) {
                profile = new UserProfile();
                profile.setAccountId(accountId);
            }

            if (command.name() != null) {
                profile.setName(command.name());
            }
            if (command.avatar() != null) {
                profile.setAvatar(command.avatar());
            }
            if (command.bio() != null) {
                profile.setBio(command.bio());
            }
            if (command.company() != null) {
                profile.setCompany(command.company());
            }
            if (command.github() != null) {
                profile.setGithub(command.github());
            }
            if (command.location() != null) {
                profile.setLocation(command.location());
            }
            if (command.twitter() != null) {
                profile.setTwitter(command.twitter());
            }
            if (command.website() != null) {
                profile.setWebsite(command.website());
            }
            if (command.preferredLanguage() != null) {
                profile.setPreferredLanguage(command.preferredLanguage());
            }

            if (isNew) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }

            log.info("Profile updated for account: {}", accountId);

            ProfileWriteResult result = new ProfileWriteResult(
                    profile.getAccountId(),
                    profile.getName(),
                    profile.getAvatar(),
                    profile.getBio(),
                    profile.getCompany(),
                    profile.getGithub(),
                    profile.getLocation(),
                    profile.getTwitter(),
                    profile.getWebsite(),
                    profile.getPreferredLanguage());

            return RpcResult.success(result, traceId);

        } catch (Exception e) {
            log.error("Profile update failed for account: {}", command.accountId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    public RpcResult<ProfileWriteResult> uploadAvatar(UploadAvatarCommand command) {
        String traceId = command.trace() != null ? command.trace().traceId() : null;
        try {
            String accountId = command.accountId();

            UserProfile profile = userProfileMapper.selectById(accountId);
            boolean isNew = profile == null;
            if (isNew) {
                profile = new UserProfile();
                profile.setAccountId(accountId);
            }
            profile.setAvatar(command.avatarUrl());

            if (isNew) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }

            log.info("Avatar updated for account: {}", accountId);

            ProfileWriteResult result = new ProfileWriteResult(
                    profile.getAccountId(),
                    profile.getName(),
                    profile.getAvatar(),
                    profile.getBio(),
                    profile.getCompany(),
                    profile.getGithub(),
                    profile.getLocation(),
                    profile.getTwitter(),
                    profile.getWebsite(),
                    profile.getPreferredLanguage());

            return RpcResult.success(result, traceId);

        } catch (Exception e) {
            log.error("Avatar update failed for account: {}", command.accountId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    private static String safeTraceId(UpdateProfileCommand command) {
        TraceMetadata trace = command.trace();
        return trace != null ? trace.traceId() : null;
    }
}
