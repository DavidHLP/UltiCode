package com.ulticode.app.userprofile;

import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import com.ulticode.modules.user.port.AvatarUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Transaction-owned App profile mutation seam shared by HTTP and RPC
 * adapters. Object storage remains outside this module and its transaction.
 */
@Service
@RequiredArgsConstructor
public class ProfileMutationModule {

    public static final String USER_STATS_CACHE = "userStats";
    public static final String CONTEST_RANKING_CACHE = "contestRanking";

    private final UserProfileMapper userProfileMapper;
    private final StorageCleanupOutbox storageCleanupOutbox;
    private final UserDirectoryQueryPort userDirectoryQueryPort;
    private final SearchDocumentChangedPublisher searchPublisher;

    public static ProfilePatch profilePatch(
            String accountId, String name, String avatar, String bio, String company,
            String github, String location, String twitter, String website, String preferredLanguage) {
        return new ProfilePatch(
                accountId, name, avatar, bio, company, github, location, twitter, website, preferredLanguage);
    }

    @Transactional
    public ProfileWriteResult update(ProfilePatch patch) {
        Objects.requireNonNull(patch, "patch");
        String accountId = requireAccountId(patch.accountId());
        UserProfile profile = userProfileMapper.selectByIdForUpdate(accountId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(accountId);
        }

        applyPatch(profile, patch);
        persist(profile, isNew);
        enqueueDisplacedAvatar(accountId, previousAvatar, profile.getAvatar());
        publishUserDocument(accountId);
        return toResult(profile);
    }

    @Transactional
    public ProfileWriteResult replaceAvatar(String accountId, String avatarReference) {
        accountId = requireAccountId(accountId);
        Objects.requireNonNull(avatarReference, "avatarReference");
        UserProfile profile = userProfileMapper.selectByIdForUpdate(accountId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(accountId);
        }
        profile.setAvatar(avatarReference);

        persist(profile, isNew);
        enqueueDisplacedAvatar(accountId, previousAvatar, profile.getAvatar());
        publishUserDocument(accountId);
        return toResult(profile);
    }

    private static void applyPatch(UserProfile profile, ProfilePatch patch) {
        if (patch.name() != null) {
            profile.setName(patch.name());
        }
        if (patch.avatar() != null) {
            if (AvatarUrls.reusesOwnedKey(profile.getAccountId(), patch.avatar(), profile.getAvatar())) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "Avatar changes must use the avatar upload endpoint");
            }
            profile.setAvatar(patch.avatar());
        }
        if (patch.bio() != null) {
            profile.setBio(patch.bio());
        }
        if (patch.company() != null) {
            profile.setCompany(patch.company());
        }
        if (patch.github() != null) {
            profile.setGithub(patch.github());
        }
        if (patch.location() != null) {
            profile.setLocation(patch.location());
        }
        if (patch.twitter() != null) {
            profile.setTwitter(patch.twitter());
        }
        if (patch.website() != null) {
            profile.setWebsite(patch.website());
        }
        if (patch.preferredLanguage() != null) {
            profile.setPreferredLanguage(patch.preferredLanguage());
        }
    }

    private void persist(UserProfile profile, boolean isNew) {
        int affectedRows = isNew
                ? userProfileMapper.insert(profile)
                : userProfileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw new IllegalStateException("Profile write affected " + affectedRows + " rows");
        }
    }

    private void enqueueDisplacedAvatar(
            String accountId, String previousAvatar, String currentAvatar) {
        String previousKey = AvatarUrls.objectKey(accountId, previousAvatar);
        String currentKey = AvatarUrls.objectKey(accountId, currentAvatar);
        if (currentKey == null) {
            currentKey = currentAvatar;
        }
        if (previousKey != null && !previousKey.equals(currentKey)) {
            storageCleanupOutbox.enqueue(previousKey);
        }
    }

    private void publishUserDocument(String accountId) {
        if (userDirectoryQueryPort == null || searchPublisher == null) {
            throw new IllegalStateException("Profile directory publication is unavailable");
        }
        var directoryRow = userDirectoryQueryPort.findById(accountId);
        if (directoryRow == null) {
            return;
        }
        var row = directoryRow.row();
        searchPublisher.publishUser(row.getId(), row.getUsername(), row.getName(), row.getAvatar(), true);
    }

    private static String requireAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "accountId is required");
        }
        return accountId;
    }

    private static ProfileWriteResult toResult(UserProfile profile) {
        return new ProfileWriteResult(
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
    }
}
