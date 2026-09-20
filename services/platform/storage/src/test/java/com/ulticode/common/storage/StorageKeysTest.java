package com.ulticode.common.storage;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageKeysTest {

    @Test
    void rejectsTraversalAbsoluteAndMalformedSegments() {
        for (String key : new String[]{"../escape", "a/../escape", "/absolute", "\\absolute",
                "a\\b", "a//b", "a/./b", "a/:bad", "a/\u0000b"}) {
            assertThatThrownBy(() -> StorageKeys.validate(key)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void buildsAndParsesAvatarKey() {
        String key = StorageKeys.avatarKey("account-1", "uuid.png");
        assertThat(key).isEqualTo("app/avatars/account-1/uuid.png");
        assertThat(StorageKeys.isAvatarKey(key)).isTrue();
        assertThat(StorageKeys.avatarAccountId(key)).isEqualTo("account-1");
        assertThat(StorageKeys.avatarObjectName(key)).isEqualTo("uuid.png");
        assertThat(StorageKeys.avatarDisplayPath("account-1", "uuid.png"))
                .isEqualTo("/api/users/avatars/account-1/uuid.png");
    }

    @Test
    void buildsBackupKey() {
        assertThat(StorageKeys.backupKey("backup-1", LocalDate.of(2026, 9, 20)))
                .isEqualTo("admin/backups/2026/09/backup-1.sql");
    }
}
