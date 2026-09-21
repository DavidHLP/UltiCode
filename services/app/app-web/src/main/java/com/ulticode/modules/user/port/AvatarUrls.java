package com.ulticode.modules.user.port;

import com.ulticode.common.storage.StorageKeys;

/** Converts persisted avatar values into the browser-facing read path. */
public final class AvatarUrls {

    private static final String LEGACY_PREFIX = "/uploads" + "/avatars/";

    private AvatarUrls() {
    }

    public static String resolve(String accountId, String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (StorageKeys.isAvatarKey(stored)) {
            String storedAccount = StorageKeys.avatarAccountId(stored);
            if (accountId == null || !accountId.equals(storedAccount)) {
                return null;
            }
            return StorageKeys.avatarDisplayPath(storedAccount, StorageKeys.avatarObjectName(stored));
        }
        if (stored.startsWith(LEGACY_PREFIX) && accountId != null && !accountId.isBlank()) {
            String objectName = stored.substring(LEGACY_PREFIX.length());
            try {
                StorageKeys.avatarKey(accountId, objectName);
                return StorageKeys.avatarDisplayPath(accountId, objectName);
            } catch (IllegalArgumentException ignored) {
                return stored;
            }
        }
        return stored;
    }

    public static String objectKey(String accountId, String stored) {
        if (StorageKeys.isAvatarKey(stored)) {
            return accountId != null && accountId.equals(StorageKeys.avatarAccountId(stored)) ? stored : null;
        }
        if (stored != null && stored.startsWith(LEGACY_PREFIX) && accountId != null && !accountId.isBlank()) {
            String objectName = stored.substring(LEGACY_PREFIX.length());
            try {
                return StorageKeys.avatarKey(accountId, objectName);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
