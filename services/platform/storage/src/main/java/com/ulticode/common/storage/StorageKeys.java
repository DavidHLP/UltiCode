package com.ulticode.common.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Central object-key grammar for every storage implementation and consumer. */
public final class StorageKeys {

    public static final String AVATAR_PREFIX = "app/avatars/";
    public static final String BACKUP_PREFIX = "admin/backups/";
    private static final DateTimeFormatter BACKUP_YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter BACKUP_MONTH = DateTimeFormatter.ofPattern("MM");

    private StorageKeys() {
    }

    public static void validate(String key) {
        if (key == null || key.isBlank() || key.length() > 512
                || key.startsWith("/") || key.startsWith("\\") || key.contains("\\")) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.indexOf(':') >= 0
                    || segment.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Illegal storage key");
            }
        }
    }

    public static String avatarKey(String accountId, String objectName) {
        if (accountId == null || objectName == null) {
            throw new IllegalArgumentException("Avatar key parts are required");
        }
        String key = AVATAR_PREFIX + accountId + "/" + objectName;
        validate(key);
        if (objectName.indexOf('/') >= 0 || objectName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Illegal avatar object name");
        }
        return key;
    }

    public static String backupKey(String backupId, LocalDate date) {
        Objects.requireNonNull(date, "date");
        String key = BACKUP_PREFIX + date.format(BACKUP_YEAR) + "/" + date.format(BACKUP_MONTH)
                + "/" + Objects.requireNonNull(backupId, "backupId") + ".sql";
        validate(key);
        return key;
    }

    public static boolean isAvatarKey(String value) {
        try {
            parseAvatar(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String avatarAccountId(String key) {
        return parseAvatar(key)[0];
    }

    public static String avatarObjectName(String key) {
        return parseAvatar(key)[1];
    }

    public static String avatarDisplayPath(String accountId, String objectName) {
        avatarKey(accountId, objectName);
        return "/api/users/avatars/" + accountId + "/" + objectName;
    }

    private static String[] parseAvatar(String key) {
        validate(key);
        if (!key.startsWith(AVATAR_PREFIX)) {
            throw new IllegalArgumentException("Not an avatar key");
        }
        String[] parts = key.substring(AVATAR_PREFIX.length()).split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()
                || parts[1].indexOf('.') <= 0 || parts[1].endsWith(".")) {
            throw new IllegalArgumentException("Invalid avatar key");
        }
        return parts;
    }
}
